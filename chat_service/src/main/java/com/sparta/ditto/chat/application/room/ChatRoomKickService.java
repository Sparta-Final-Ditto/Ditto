package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomKickResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomEventPublisher;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatCannotKickSelfException;
import com.sparta.ditto.chat.domain.exception.ChatKickForbiddenException;
import com.sparta.ditto.chat.domain.exception.ChatNotGroupRoomException;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoomInactiveException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomKickService {

    private static final String SYSTEM_KICK_CONTENT = "사용자가 강퇴되었습니다.";

    private final ChatMessageSendService chatMessageSendService;
    private final ChatRoomEventPublisher chatRoomEventPublisher;
    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;

    @Transactional
    public ChatRoomKickResult kick(UUID requesterId, UUID roomId, UUID targetUserId) {
        if (requesterId == null || roomId == null || targetUserId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (requesterId.equals(targetUserId)) {
            throw new ChatCannotKickSelfException();
        }

        ChatRoom chatRoom = chatRoomPort.findById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);
        if (chatRoom.getRoomType() != RoomType.GROUP) {
            throw new ChatNotGroupRoomException();
        }
        if (chatRoom.getStatus() != RoomStatus.ACTIVE) {
            throw new ChatRoomInactiveException();
        }

        // 요청자는 방의 활성 참여자이면서 OWNER여야 강퇴 가능
        ChatRoomParticipant requester = chatRoomParticipantPort
                .findActiveParticipant(roomId, requesterId)
                .orElseThrow(ChatNotParticipantException::new);
        if (requester.getRole() != ParticipantRole.OWNER) {
            throw new ChatKickForbiddenException();
        }

        ChatRoomParticipant target = chatRoomParticipantPort
                .findActiveParticipant(roomId, targetUserId)
                .orElseThrow(ChatNotParticipantException::new);

        // 강퇴 대상 기준으로 시스템 메시지를 남기고 퇴장 처리
        String lastVisibleMessageId = saveSystemKickMessage(roomId, targetUserId);
        target.leave(lastVisibleMessageId);

        // OWNER는 그대로 남아 있으므로 방은 ACTIVE를 유지
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    chatRoomEventPublisher.notifyLeft(targetUserId, roomId);
                } catch (Exception ex) {
                    log.warn("Failed to publish room-kicked signal. targetUserId={}, roomId={}",
                            targetUserId, roomId, ex);
                }
            }
        });

        log.info("Chat room participant kicked. requesterId={}, targetUserId={}, roomId={}",
                requesterId, targetUserId, roomId);

        return ChatRoomKickResult.of(
                chatRoom.getId(),
                chatRoom.getStatus(),
                targetUserId,
                target.getLeftAt(),
                lastVisibleMessageId
        );
    }

    private String saveSystemKickMessage(UUID roomId, UUID targetUserId) {
        SentMessage systemMessage = chatMessageSendService.saveSystemMessage(
                roomId,
                targetUserId,
                MessageType.SYSTEM_KICK,
                SYSTEM_KICK_CONTENT
        );
        return systemMessage.messageId();
    }
}
