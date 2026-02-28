package com.chatapp.service;

import com.chatapp.model.UserSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory store of online user sessions.
 * Key: WebSocket session ID → Value: UserSession
 */
@Slf4j
@Service
public class OnlineUserService {

    // sessionId → UserSession
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();

    public void addUser(String sessionId, String username, String roomId) {
        sessions.put(sessionId, UserSession.builder()
                .sessionId(sessionId)
                .username(username)
                .roomId(roomId)
                .connectedAt(System.currentTimeMillis())
                .build());
        log.debug("User added: {} in room {} (session={})", username, roomId, sessionId);
    }

    public void removeUser(String sessionId) {
        UserSession removed = sessions.remove(sessionId);
        if (removed != null) {
            log.debug("User removed: {} (session={})", removed.getUsername(), sessionId);
        }
    }

    public void changeRoom(String sessionId, String newRoomId) {
        UserSession session = sessions.get(sessionId);
        if (session != null) {
            session.setRoomId(newRoomId);
        }
    }

    /** Returns usernames of all users in a specific room */
    public List<String> getUsersInRoom(String roomId) {
        return sessions.values().stream()
                .filter(s -> roomId.equals(s.getRoomId()))
                .map(UserSession::getUsername)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /** Returns count of online users in a room */
    public int getOnlineCountInRoom(String roomId) {
        return (int) sessions.values().stream()
                .filter(s -> roomId.equals(s.getRoomId()))
                .map(UserSession::getUsername)
                .distinct()
                .count();
    }

    /** Returns total unique online users across all rooms */
    public int getTotalOnlineCount() {
        return (int) sessions.values().stream()
                .map(UserSession::getUsername)
                .distinct()
                .count();
    }

    public Optional<UserSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public boolean isUsernameInRoom(String username, String roomId) {
        return sessions.values().stream()
                .anyMatch(s -> username.equals(s.getUsername()) && roomId.equals(s.getRoomId()));
    }
}
