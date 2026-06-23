package com.sparta.ditto.chat.application.room.port;

import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomPort {

    Optional<ChatRoom> findById(UUID roomId);

    boolean existsById(UUID roomId);

    ChatRoom save(ChatRoom chatRoom);

    ChatRoom saveForUniqueCheck(ChatRoom chatRoom);

    List<ChatRoom> findAllByIdsOrderByLastMessageAtDesc(Collection<UUID> roomIds);
}
