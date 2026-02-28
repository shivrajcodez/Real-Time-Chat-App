package com.chatapp.controller;

import com.chatapp.model.ChatDTOs;
import com.chatapp.service.MessageService;
import com.chatapp.service.OnlineUserService;
import com.chatapp.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final MessageService messageService;
    private final OnlineUserService onlineUserService;

    /** Get all rooms with live online counts */
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatDTOs.RoomPayload>> getRooms() {
        return ResponseEntity.ok(roomService.getAllRooms());
    }

    /** Get message history for a room */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> getRoomMessages(@PathVariable String roomId) {
        if (!roomService.roomExists(roomId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(messageService.getRecentMessages(roomId));
    }

    /** Get users in a room */
    @GetMapping("/rooms/{roomId}/users")
    public ResponseEntity<?> getRoomUsers(@PathVariable String roomId) {
        if (!roomService.roomExists(roomId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "roomId", roomId,
                "users", onlineUserService.getUsersInRoom(roomId),
                "count", onlineUserService.getOnlineCountInRoom(roomId)
        ));
    }

    /** Get stats */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalOnline", onlineUserService.getTotalOnlineCount(),
                "rooms", roomService.getAllRooms().size()
        ));
    }

    /** Create a new room */
    @PostMapping("/rooms")
    public ResponseEntity<?> createRoom(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.getOrDefault("description", "");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name is required"));
        }
        var room = roomService.createRoom(name, name, description);
        return ResponseEntity.ok(room);
    }
}
