package com.chatapp.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String roomId;

    @Enumerated(EnumType.STRING)
    private MessageType type;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    public enum MessageType {
        CHAT,       // Regular chat message
        JOIN,       // User joined room
        LEAVE,      // User left room
        SYSTEM      // System notification
    }
}
