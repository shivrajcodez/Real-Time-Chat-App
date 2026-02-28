package com.chatapp.controller;

import com.chatapp.model.ChatDTOs;
import com.chatapp.model.Message;
import com.chatapp.service.MessageService;
import com.chatapp.service.OnlineUserService;
import com.chatapp.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * Handles all inbound WebSocket messages from clients.
 *
 * Flow:
 *  Client → /app/chat.join       → join a room
 *  Client → /app/chat.send       → broadcast a message
 *  Client → /app/chat.typing     → broadcast typing indicator
 *  Client → /app/chat.leave      → leave a room
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final OnlineUserService onlineUserService;
    private final RoomService roomService;

    // ── Join Room ──────────────────────────────────────────────────────────────

    @MessageMapping("/chat.join")
    public void joinRoom(@Payload ChatDTOs.JoinRequest request,
                         SimpMessageHeaderAccessor headerAccessor) {

        String sessionId = headerAccessor.getSessionId();
        String username = sanitize(request.getUsername());
        String roomId = request.getRoomId();

        // Validate
        if (username == null || username.isBlank() || !roomService.roomExists(roomId)) {
            sendError(sessionId, "Invalid username or room ID");
            return;
        }

        // Store in session attributes for disconnect handling
        Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
        if (sessionAttrs != null) {
            sessionAttrs.put("username", username);
            sessionAttrs.put("roomId", roomId);
        }

        // Register online user
        onlineUserService.addUser(sessionId, username, roomId);

        log.info("User '{}' joined room '{}'", username, roomId);

        // 1. Send message history to the joining user (directly to their session)
        var history = messageService.getRecentMessages(roomId);
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/history",
                ChatDTOs.HistoryPayload.builder().roomId(roomId).messages(history).build(),
                buildNativeHeaders(sessionId));

        // 2. Broadcast JOIN system message to room
        var joinMsg = buildSystemMessage(username + " joined the room", roomId, Message.MessageType.JOIN);
        messageService.saveMessageAsync(joinMsg.getContent(), joinMsg.getSender(),
                roomId, Message.MessageType.JOIN);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, joinMsg);

        // 3. Broadcast updated user list
        broadcastUserList(roomId);
    }

    // ── Send Message ───────────────────────────────────────────────────────────

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatDTOs.SendMessageRequest request) {

        String content = sanitize(request.getContent());
        String sender = sanitize(request.getSender());
        String roomId = request.getRoomId();

        if (content == null || content.isBlank() || sender == null || sender.isBlank()) {
            return;
        }

        log.debug("Message from '{}' in room '{}': {}", sender, roomId, content);

        // Async persist — don't block the WebSocket thread
        messageService.saveMessageAsync(content, sender, roomId, Message.MessageType.CHAT)
                .thenAccept(saved -> log.debug("Message id={} persisted", saved.getId()));

        // Broadcast immediately (don't wait for persistence)
        var payload = ChatDTOs.MessagePayload.builder()
                .content(content)
                .sender(sender)
                .roomId(roomId)
                .type(Message.MessageType.CHAT)
                .timestamp(java.time.LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);
    }

    // ── Typing Indicator ───────────────────────────────────────────────────────

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload ChatDTOs.TypingEvent event) {
        // Forward typing event to all room subscribers (except sender via client-side filtering)
        messagingTemplate.convertAndSend("/topic/room/" + event.getRoomId() + "/typing", event);
    }

    // ── Leave Room ─────────────────────────────────────────────────────────────

    @MessageMapping("/chat.leave")
    public void leaveRoom(@Payload ChatDTOs.JoinRequest request,
                          SimpMessageHeaderAccessor headerAccessor) {

        String username = sanitize(request.getUsername());
        String roomId = request.getRoomId();
        String sessionId = headerAccessor.getSessionId();

        onlineUserService.removeUser(sessionId);

        // Broadcast LEAVE system message
        var leaveMsg = buildSystemMessage(username + " left the room", roomId, Message.MessageType.LEAVE);
        messageService.saveMessageAsync(leaveMsg.getContent(), leaveMsg.getSender(),
                roomId, Message.MessageType.LEAVE);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, leaveMsg);

        // Broadcast updated user list
        broadcastUserList(roomId);

        log.info("User '{}' left room '{}'", username, roomId);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void broadcastUserList(String roomId) {
        var users = onlineUserService.getUsersInRoom(roomId);
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/users",
                ChatDTOs.UsersPayload.builder().roomId(roomId).users(users).build());
    }

    private ChatDTOs.MessagePayload buildSystemMessage(String content, String roomId,
                                                        Message.MessageType type) {
        return ChatDTOs.MessagePayload.builder()
                .content(content)
                .sender("System")
                .roomId(roomId)
                .type(type)
                .timestamp(java.time.LocalDateTime.now())
                .build();
    }

    private void sendError(String sessionId, String message) {
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/errors",
                ChatDTOs.ErrorPayload.builder().message(message).code("BAD_REQUEST").build(),
                buildNativeHeaders(sessionId));
    }

    /** Build headers that correctly target a session ID when using convertAndSendToUser */
    private org.springframework.messaging.MessageHeaders buildNativeHeaders(String sessionId) {
        var headerAccessor = org.springframework.messaging.simp.SimpMessageHeaderAccessor.create(
                org.springframework.messaging.simp.SimpMessageType.MESSAGE);
        headerAccessor.setSessionId(sessionId);
        headerAccessor.setLeaveMutable(true);
        return headerAccessor.getMessageHeaders();
    }

    private String sanitize(String input) {
        if (input == null) return null;
        // Basic XSS prevention
        return input.trim()
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
