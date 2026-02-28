package com.chatapp.service;

import com.chatapp.model.ChatDTOs;
import com.chatapp.model.Message;
import com.chatapp.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int HISTORY_LIMIT = 50;

    private final MessageRepository messageRepository;

    /**
     * Persist a message asynchronously so the WebSocket handler returns immediately.
     */
    @Async("messageExecutor")
    @Transactional
    public CompletableFuture<Message> saveMessageAsync(String content, String sender,
                                                        String roomId, Message.MessageType type) {
        Message message = Message.builder()
                .content(content)
                .sender(sender)
                .roomId(roomId)
                .type(type)
                .timestamp(LocalDateTime.now())
                .build();
        Message saved = messageRepository.save(message);
        log.debug("Persisted message id={} in room={}", saved.getId(), roomId);
        return CompletableFuture.completedFuture(saved);
    }

    /**
     * Retrieve the last N messages for a room (for history on join).
     */
    @Transactional(readOnly = true)
    public List<ChatDTOs.MessagePayload> getRecentMessages(String roomId) {
        return messageRepository.findLastMessagesByRoomId(
                roomId, PageRequest.of(0, HISTORY_LIMIT))
                .stream()
                .map(this::toPayload)
                .collect(Collectors.toList());
    }

    public ChatDTOs.MessagePayload toPayload(Message message) {
        return ChatDTOs.MessagePayload.builder()
                .id(message.getId())
                .content(message.getContent())
                .sender(message.getSender())
                .roomId(message.getRoomId())
                .type(message.getType())
                .timestamp(message.getTimestamp())
                .build();
    }

    public long getMessageCount(String roomId) {
        return messageRepository.countByRoomId(roomId);
    }
}
