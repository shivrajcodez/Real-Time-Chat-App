package com.chatapp.service;

import com.chatapp.model.ChatRoom;
import com.chatapp.model.ChatDTOs;
import com.chatapp.repository.ChatRoomRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final OnlineUserService onlineUserService;

    /** Seed default rooms on startup */
    @PostConstruct
    public void initDefaultRooms() {
        if (chatRoomRepository.count() == 0) {
            List<ChatRoom> defaultRooms = List.of(
                ChatRoom.builder().id("general").name("# general")
                    .description("General discussion for everyone").build(),
                ChatRoom.builder().id("tech").name("# tech")
                    .description("Technology, code, and geek talk").build(),
                ChatRoom.builder().id("random").name("# random")
                    .description("Anything goes — memes, off-topic, fun").build(),
                ChatRoom.builder().id("announcements").name("📢 announcements")
                    .description("Important updates and news").build()
            );
            chatRoomRepository.saveAll(defaultRooms);
            log.info("Seeded {} default rooms", defaultRooms.size());
        }
    }

    public List<ChatDTOs.RoomPayload> getAllRooms() {
        return chatRoomRepository.findAll().stream()
                .map(room -> ChatDTOs.RoomPayload.builder()
                        .id(room.getId())
                        .name(room.getName())
                        .description(room.getDescription())
                        .onlineCount(onlineUserService.getOnlineCountInRoom(room.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    public Optional<ChatRoom> getRoom(String roomId) {
        return chatRoomRepository.findById(roomId);
    }

    public ChatRoom createRoom(String id, String name, String description) {
        ChatRoom room = ChatRoom.builder()
                .id(id.toLowerCase().replaceAll("[^a-z0-9-]", "-"))
                .name(name)
                .description(description)
                .build();
        return chatRoomRepository.save(room);
    }

    public boolean roomExists(String roomId) {
        return chatRoomRepository.existsById(roomId);
    }
}
