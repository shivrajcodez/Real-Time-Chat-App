package com.chatapp.repository;

import com.chatapp.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Fetch the last N messages for a room, ordered by timestamp ascending.
     */
    @Query("SELECT m FROM Message m WHERE m.roomId = :roomId ORDER BY m.timestamp ASC")
    List<Message> findLastMessagesByRoomId(@Param("roomId") String roomId,
                                           org.springframework.data.domain.Pageable pageable);

    /**
     * Count messages in a room.
     */
    long countByRoomId(String roomId);

    /**
     * Delete all messages in a room.
     */
    void deleteByRoomId(String roomId);
}
