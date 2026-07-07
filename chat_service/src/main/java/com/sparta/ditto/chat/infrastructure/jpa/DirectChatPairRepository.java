package com.sparta.ditto.chat.infrastructure.jpa;

import com.sparta.ditto.chat.domain.room.DirectChatPair;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectChatPairRepository extends JpaRepository<DirectChatPair, UUID> {

    // 채팅방 ID로 1:1 채팅 pair 메타데이터를 확인할 때 사용한다.
    Optional<DirectChatPair> findByRoomId(UUID roomId);

    // 정규화된 user1Id, user2Id 기준으로 기존 1:1 채팅방을 찾을 때 사용한다.
    Optional<DirectChatPair> findByUser1IdAndUser2Id(UUID user1Id, UUID user2Id);
}
