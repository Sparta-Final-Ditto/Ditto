package com.sparta.ditto.chat.infrastructure.jpa;

import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    // 내 방 목록 조회용. 마지막 메시지가 없는 방은 뒤로 보내고, updatedAt으로 보조 정렬한다.
    @Query("""
            SELECT room
            FROM ChatRoom room
            WHERE room.id IN :roomIds
            ORDER BY
                CASE WHEN room.lastMessageAt IS NULL THEN 1 ELSE 0 END,
                room.lastMessageAt DESC,
                room.updatedAt DESC
            """)
    List<ChatRoom> findAllByIdsOrderByLastMessageAtDesc(
            @Param("roomIds") Collection<UUID> roomIds
    );
}
