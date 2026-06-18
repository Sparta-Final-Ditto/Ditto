package com.sparta.ditto.chat.infrastructure.jpa;

import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
}
