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
        // 1단계: 모든 대상의 참여자 등록·검증을 먼저 끝낸다.
        // 중간에 예외(이미 활성 참여자 등)가 나면 시스템 메시지(MongoDB) 저장 전에 롤백된다.
        for (InvitedTarget target : targets) {
            registerParticipant(chatRoom, target.userId());
        }

        // 2단계: 참여자 등록이 모두 성공한 뒤에만 초대 안내 시스템 메시지를 저장한다.
        for (InvitedTarget target : targets) {
            chatMessageSendService.saveSystemMessage(
                    chatRoom.getId(),
                    target.userId(),
                    MessageType.SYSTEM_INVITE,
                    String.format(SYSTEM_INVITE_CONTENT_FORMAT, target.nickname())
            );
        }
    }

    private void registerParticipant(ChatRoom chatRoom, UUID targetUserId) {
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
