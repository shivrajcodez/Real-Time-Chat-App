package com.chatapp.config;

import com.chatapp.model.UserSession;
import com.chatapp.service.OnlineUserService;
import com.chatapp.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final OnlineUserService onlineUserService;
    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        log.debug("New WebSocket connection: sessionId={}", sessionId);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();

        // Retrieve stored session attributes
        Map<String, Object> sessionAttributes = headers.getSessionAttributes();
        if (sessionAttributes == null) return;

        String username = (String) sessionAttributes.get("username");
        String roomId = (String) sessionAttributes.get("roomId");

        if (username != null) {
            log.debug("User disconnected: username={}, room={}", username, roomId);
            onlineUserService.removeUser(sessionId);

            // Broadcast updated user list to the room they were in
            if (roomId != null) {
                messagingTemplate.convertAndSend(
                    "/topic/room/" + roomId + "/users",
                    onlineUserService.getUsersInRoom(roomId)
                );
            }

            // Broadcast global online user count update
            messagingTemplate.convertAndSend("/topic/online-count",
                Map.of("count", onlineUserService.getTotalOnlineCount()));
        }
    }
}
