package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.domain.exception.ChatAlreadyParticipantException;
import com.sparta.ditto.chat.domain.message.MessageType;
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

    private static final String SYSTEM_INVITE_CONTENT_FORMAT = "%s님이 초대되었습니다.";

    private final ChatRoomParticipantPort chatRoomParticipantPort;
    private final ChatMessageSendService chatMessageSendService;

    @Transactional
    public void register(ChatRoom chatRoom, List<InvitedTarget> targets) {
        for (InvitedTarget target : targets) {
            inviteParticipant(chatRoom, target);
        }
    }

    private void inviteParticipant(ChatRoom chatRoom, InvitedTarget target) {
        UUID roomId = chatRoom.getId();
        UUID targetUserId = target.userId();
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

        // 초대 안내 시스템 메시지 저장
        chatMessageSendService.saveSystemMessage(
                roomId,
                targetUserId,
                MessageType.SYSTEM_INVITE,
                String.format(SYSTEM_INVITE_CONTENT_FORMAT, target.nickname())
        );
    }

    private void markReadUpToCurrentMessage(ChatRoomParticipant participant, ChatRoom chatRoom) {
        if (chatRoom.getLastMessageId() != null && chatRoom.getLastMessageAt() != null) {
            participant.updateLastRead(chatRoom.getLastMessageId(), chatRoom.getLastMessageAt());
        }
    }
}
