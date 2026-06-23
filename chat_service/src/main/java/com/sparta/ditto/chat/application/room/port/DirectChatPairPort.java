package com.sparta.ditto.chat.application.room.port;

import com.sparta.ditto.chat.domain.room.DirectChatPair;
import java.util.Optional;
import java.util.UUID;

public interface DirectChatPairPort {

    Optional<DirectChatPair> findByRoomId(UUID roomId);

    Optional<DirectChatPair> findByOrderedUserIds(UUID user1Id, UUID user2Id);

    DirectChatPair saveAndFlush(DirectChatPair directChatPair);
}
