package com.chatapp.model;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * All DTOs (Data Transfer Objects) used in WebSocket communication.
 */
public class ChatDTOs {

    // ── Inbound (Client → Server) ─────────────────────────────────────────────

    /** Sent when a user joins a room */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class JoinRequest {
        private String username;
        private String roomId;
    }

    /** Sent when a user sends a chat message */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SendMessageRequest {
        private String content;
        private String sender;
        private String roomId;
    }

    /** Sent when typing status changes */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TypingEvent {
        private String username;
        private String roomId;
        private boolean typing;
    }

    // ── Outbound (Server → Client) ────────────────────────────────────────────

    /** Full message payload sent to subscribers */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MessagePayload {
        private Long id;
        private String content;
        private String sender;
        private String roomId;
        private Message.MessageType type;
        private LocalDateTime timestamp;
    }

    /** Room info payload */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RoomPayload {
        private String id;
        private String name;
        private String description;
        private int onlineCount;
    }

    /** User list payload for a room */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UsersPayload {
        private String roomId;
        private List<String> users;
    }

    /** Online count payload */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class OnlineCountPayload {
        private int count;
    }

    /** Error payload */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ErrorPayload {
        private String message;
        private String code;
    }

    /** History payload — list of past messages on room join */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HistoryPayload {
        private String roomId;
        private List<MessagePayload> messages;
    }
}
