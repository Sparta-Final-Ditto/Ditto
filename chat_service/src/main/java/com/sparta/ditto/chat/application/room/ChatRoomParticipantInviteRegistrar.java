package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.domain.exception.ChatAlreadyParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
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
    public void register(ChatRoom chatRoom, List<UUID> targetUserIds) {
        for (UUID targetUserId : targetUserIds) {
            inviteParticipant(chatRoom, targetUserId);
        }
    }

    private void inviteParticipant(ChatRoom chatRoom, UUID targetUserId) {
        UUID roomId = chatRoom.getId();
        Optional<ChatRoomParticipant> existing =
                chatRoomParticipantPort.findByRoomIdAndUserId(roomId, targetUserId);

        ChatRoomParticipant participant;
        if (existing.isEmpty()) {
            participant = ChatRoomParticipant.join(roomId, targetUserId, ParticipantRole.MEMBER);
        } else {
            participant = existing.get();
            if (participant.getLeftAt() == null) {
                // 이미 활성 참여자인 사용자는 재초대할 수 없다.
                throw new ChatAlreadyParticipantException();
            }
            // 나간 사용자는 기존 row를 재사용해 재참여시킨다.
            participant.reInvite(ParticipantRole.MEMBER);
        }

        markReadUpToCurrentMessage(participant, chatRoom);

        chatRoomParticipantPort.save(participant);
    }

    private void markReadUpToCurrentMessage(ChatRoomParticipant participant, ChatRoom chatRoom) {
        if (chatRoom.getLastMessageId() != null && chatRoom.getLastMessageAt() != null) {
            participant.updateLastRead(chatRoom.getLastMessageId(), chatRoom.getLastMessageAt());
        }
    }
}
