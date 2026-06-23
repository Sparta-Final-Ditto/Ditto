package com.sparta.ditto.chat.infrastructure.jpa;

import com.sparta.ditto.chat.application.room.port.DirectChatPairPort;
import com.sparta.ditto.chat.domain.room.DirectChatPair;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DirectChatPairPersistenceAdapter implements DirectChatPairPort {

    private final DirectChatPairRepository directChatPairRepository;

    @Override
    public Optional<DirectChatPair> findByRoomId(UUID roomId) {
        return directChatPairRepository.findByRoomId(roomId);
    }

    @Override
    public Optional<DirectChatPair> findByOrderedUserIds(UUID user1Id, UUID user2Id) {
        return directChatPairRepository.findByUser1IdAndUser2Id(user1Id, user2Id);
    }

    @Override
    public DirectChatPair saveAndFlush(DirectChatPair directChatPair) {
        return directChatPairRepository.saveAndFlush(directChatPair);
    }
}
