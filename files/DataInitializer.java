package com.chatapp.config;

import com.chatapp.model.Message;
import com.chatapp.repository.MessageRepository;
import com.chatapp.service.RoomService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final MessageRepository messageRepository;
    private final RoomService roomService;

    @PostConstruct
    public void seed() {
        // Give RoomService time to init (both use @PostConstruct; ordering not guaranteed)
        // so we check and seed after rooms exist.
        seedWelcomeMessages();
    }

    private void seedWelcomeMessages() {
        if (messageRepository.count() > 0) return;

        List<Message> welcomeMessages = List.of(
            buildMsg("Welcome to ChatWave! 🎉 This is the general channel.", "System", "general", Message.MessageType.SYSTEM),
            buildMsg("Feel free to chat, share ideas, and connect with others.", "System", "general", Message.MessageType.SYSTEM),
            buildMsg("Check out #tech for programming discussions!", "System", "general", Message.MessageType.SYSTEM),

            buildMsg("Welcome to the tech channel! Discuss code, tools, and all things tech.", "System", "tech", Message.MessageType.SYSTEM),
            buildMsg("What's everyone building these days?", "System", "tech", Message.MessageType.SYSTEM),

            buildMsg("This is #random — anything goes. Memes, jokes, life updates!", "System", "random", Message.MessageType.SYSTEM)
        );

        messageRepository.saveAll(welcomeMessages);
        log.info("Seeded {} welcome messages", welcomeMessages.size());
    }

    private Message buildMsg(String content, String sender, String roomId, Message.MessageType type) {
        return Message.builder()
                .content(content)
                .sender(sender)
                .roomId(roomId)
                .type(type)
                .timestamp(LocalDateTime.now().minusHours(1))
                .build();
    }
}
