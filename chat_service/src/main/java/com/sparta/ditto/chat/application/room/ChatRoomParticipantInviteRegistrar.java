package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.domain.exception.ChatAlreadyParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatRoomParticipantInviteRegistrar {

    private final ChatRoomParticipantPort chatRoomParticipantPort;

    @Transactional
    public void register(UUID roomId, List<UUID> targetUserIds) {
        for (UUID targetUserId : targetUserIds) {
            inviteParticipant(roomId, targetUserId);
        }
    }

    private void inviteParticipant(UUID roomId, UUID targetUserId) {
        Optional<ChatRoomParticipant> existing =
                chatRoomParticipantPort.findByRoomIdAndUserId(roomId, targetUserId);

        if (existing.isEmpty()) {
            chatRoomParticipantPort.save(
                    ChatRoomParticipant.join(roomId, targetUserId, ParticipantRole.MEMBER)
            );
            return;
        }

        ChatRoomParticipant participant = existing.get();

        if (participant.getLeftAt() == null) {
            throw new ChatAlreadyParticipantException();
        }

        participant.reInvite(ParticipantRole.MEMBER);
        chatRoomParticipantPort.save(participant);
    }
}
