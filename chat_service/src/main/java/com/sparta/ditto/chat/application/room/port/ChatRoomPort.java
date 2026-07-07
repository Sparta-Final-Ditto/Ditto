package com.sparta.ditto.chat.application.room.port;

import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomPort {

    Optional<ChatRoom> findById(UUID roomId);

    // lastMessage 갱신 시 동시성 방어(lost update)를 위해 방 row에 쓰기 락을 걸어 조회한다.
    Optional<ChatRoom> findByIdForUpdate(UUID roomId);

    boolean existsById(UUID roomId);

    ChatRoom save(ChatRoom chatRoom);

    ChatRoom saveForUniqueCheck(ChatRoom chatRoom);

    List<ChatRoom> findAllByIdsOrderByLastMessageAtDesc(Collection<UUID> roomIds);
}
