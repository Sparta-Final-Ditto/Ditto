package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomLeaveResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomEventPublisher;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomType;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.Comparator;
import java.util.List;
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
public class ChatRoomLeaveService {

    private static final String SYSTEM_LEAVE_CONTENT = "사용자가 채팅방을 나갔습니다.";

    private final ChatMessageSendService chatMessageSendService;
    private final ChatRoomEventPublisher chatRoomEventPublisher;
    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;

    @Transactional
    public ChatRoomLeaveResult leaveRoom(UUID requesterId, UUID roomId) {
        if (requesterId == null || roomId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        ChatRoom chatRoom = chatRoomPort.findById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);
        ChatRoomParticipant requesterParticipant = chatRoomParticipantPort
                .findActiveParticipant(roomId, requesterId)
                .orElseThrow(ChatNotParticipantException::new);

        List<ChatRoomParticipant> activeParticipants =
                chatRoomParticipantPort.findActiveParticipants(roomId);
        String lastVisibleMessageId = saveSystemLeaveMessage(roomId, requesterId);

        requesterParticipant.leave(lastVisibleMessageId);

        if (chatRoom.getRoomType() == RoomType.DIRECT) {
            // 1:1 방은 한 명이 나가면 새 메시지를 막기 위해 방을 비활성화한다.
            chatRoom.inactivate(requesterId);
            log.info("Direct chat room inactivated after leave. userId={}, roomId={}",
                    requesterId, roomId);
        } else {
            leaveGroupRoom(chatRoom, requesterParticipant, activeParticipants, requesterId);
        }

        // DB 커밋 이전에 STOMP 신호를 보내면 트랜잭션이 롤백될 때 신호만 나간 상태가 된다.
        // afterCommit에서 발송해 DB 커밋이 확정된 후에만 클라이언트에 신호가 가도록 한다.
        // STOMP 발송 실패 시 DB leave는 이미 커밋된 상태라 롤백할 수 없으므로
        // 예외를 삼키고 경고 로그만 남긴다. REST 응답이 500으로 보이는 오해를 막기 위해서다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    chatRoomEventPublisher.notifyLeft(requesterId, roomId);
                } catch (Exception ex) {
                    log.warn("Failed to publish room-left signal. userId={}, roomId={}",
                            requesterId, roomId, ex);
                }
            }
        });

        log.info("Chat room left. userId={}, roomId={}, roomType={}, "
                        + "roomStatus={}, lastVisibleMessageId={}",
                requesterId, roomId, chatRoom.getRoomType(), chatRoom.getStatus(),
                lastVisibleMessageId);

        return result(chatRoom, requesterParticipant);
    }

    private void leaveGroupRoom(
            ChatRoom chatRoom,
            ChatRoomParticipant requesterParticipant,
            List<ChatRoomParticipant> activeParticipants,
            UUID requesterId
    ) {
        List<ChatRoomParticipant> remainingParticipants = activeParticipants.stream()
                .filter(participant -> !participant.getUserId().equals(requesterId))
                .toList();

        if (remainingParticipants.isEmpty()) {
            chatRoom.inactivate(requesterId);
            log.info("Group chat room inactivated because last participant left. "
                            + "userId={}, roomId={}",
                    requesterId, chatRoom.getId());
            return;
        }

        if (requesterParticipant.getRole() == ParticipantRole.OWNER) {
            // OWNER가 나가면 남은 참여자 중 가장 먼저 참여한 사용자에게 위임하고,
            // joinedAt이 같으면 userId 기준으로 결정해 항상 같은 결과가 나오게 한다.
            remainingParticipants.stream()
                    .min(Comparator.comparing(ChatRoomParticipant::getJoinedAt)
                            .thenComparing(ChatRoomParticipant::getUserId))
                    .ifPresent(newOwner -> {
                        newOwner.assignOwnerRole();
                        log.info("Group chat room owner reassigned. "
                                        + "roomId={}, previousOwnerId={}, newOwnerId={}",
                                chatRoom.getId(), requesterId, newOwner.getUserId());
                    });
        }
    }

    private ChatRoomLeaveResult result(
            ChatRoom chatRoom,
            ChatRoomParticipant requesterParticipant
    ) {
        return ChatRoomLeaveResult.of(
                chatRoom.getId(),
                chatRoom.getStatus(),
                requesterParticipant.getLeftAt(),
                requesterParticipant.getLastVisibleMessageId()
        );
    }

    private String saveSystemLeaveMessage(UUID roomId, UUID requesterId) {
        SentMessage systemMessage = chatMessageSendService.saveSystemMessage(
                roomId,
                requesterId,
                MessageType.SYSTEM_LEAVE,
                SYSTEM_LEAVE_CONTENT
        );
        return systemMessage.messageId();
    }
}
