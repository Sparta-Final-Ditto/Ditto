package com.sparta.ditto.chat.application.room.port;

import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomParticipantPort {

    Optional<ChatRoomParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId);

    Optional<ChatRoomParticipant> findActiveParticipant(UUID roomId, UUID userId);

    List<ChatRoomParticipant> findActiveParticipants(UUID roomId);

    List<ChatRoomParticipant> findAllParticipants(UUID roomId);

    List<ChatRoomParticipant> findVisibleActiveParticipantsByUserId(UUID userId);

    ChatRoomParticipant save(ChatRoomParticipant participant);

    void saveAll(Collection<ChatRoomParticipant> participants);
}
