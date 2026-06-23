package com.sparta.ditto.chat.infrastructure.jpa;

import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatRoomPersistenceAdapter implements ChatRoomPort {

    private final ChatRoomRepository chatRoomRepository;

    @Override
    public Optional<ChatRoom> findById(UUID roomId) {
        return chatRoomRepository.findById(roomId);
    }

    @Override
    public boolean existsById(UUID roomId) {
        return chatRoomRepository.existsById(roomId);
    }

    @Override
    public ChatRoom save(ChatRoom chatRoom) {
        return chatRoomRepository.save(chatRoom);
    }

    @Override
    public ChatRoom saveAndFlush(ChatRoom chatRoom) {
        return chatRoomRepository.saveAndFlush(chatRoom);
    }

    @Override
    public List<ChatRoom> findAllByIdsOrderByLastMessageAtDesc(Collection<UUID> roomIds) {
        return chatRoomRepository.findAllByIdsOrderByLastMessageAtDesc(roomIds);
    }
}
