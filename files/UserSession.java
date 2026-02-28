package com.chatapp.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSession {
    private String sessionId;
    private String username;
    private String roomId;
    private long connectedAt;
}
