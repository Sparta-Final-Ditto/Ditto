package com.sparta.ditto.chat.infrastructure.jpa;

import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatRoomParticipantPersistenceAdapter implements ChatRoomParticipantPort {

    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Override
    public Optional<ChatRoomParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId) {
        return chatRoomParticipantRepository.findByRoomIdAndUserId(roomId, userId);
    }

    @Override
    public Optional<ChatRoomParticipant> findActiveParticipant(UUID roomId, UUID userId) {
        return chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(
                roomId,
                userId
        );
    }

    @Override
    public List<ChatRoomParticipant> findActiveParticipants(UUID roomId) {
        return chatRoomParticipantRepository.findAllByRoomIdAndLeftAtIsNull(roomId);
    }

    @Override
    public List<ChatRoomParticipant> findAllParticipants(UUID roomId) {
        return chatRoomParticipantRepository.findAllByRoomId(roomId);
    }

    @Override
    public List<ChatRoomParticipant> findVisibleActiveParticipantsByUserId(UUID userId) {
        return chatRoomParticipantRepository.findAllByUserIdAndLeftAtIsNullAndHiddenFalse(
                userId
        );
    }

    @Override
    public ChatRoomParticipant save(ChatRoomParticipant participant) {
        return chatRoomParticipantRepository.save(participant);
    }

    @Override
    public void saveAll(Collection<ChatRoomParticipant> participants) {
        chatRoomParticipantRepository.saveAll(participants);
    }

    @Override
    public int incrementUnreadCountForActiveParticipantsExceptSender(
            UUID roomId,
            UUID senderId
    ) {
        return chatRoomParticipantRepository
                .incrementUnreadCountForActiveParticipantsExceptSender(roomId, senderId);
    }

    @Override
    public int markReadAndResetUnread(
            UUID roomId,
            UUID userId,
            String lastReadMessageId,
            Instant lastReadAt
    ) {
        return chatRoomParticipantRepository.markReadAndResetUnread(
                roomId, userId, lastReadMessageId, lastReadAt);
    }
}
