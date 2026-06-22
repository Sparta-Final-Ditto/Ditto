package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomLeaveResult;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomType;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomLeaveService {

    private static final String SYSTEM_LEAVE_CONTENT = "사용자가 채팅방을 나갔습니다.";

    private final ChatMessageSendService chatMessageSendService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Transactional
    public ChatRoomLeaveResult leaveRoom(UUID requesterId, UUID roomId) {
        if (requesterId == null || roomId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);
        ChatRoomParticipant requesterParticipant = chatRoomParticipantRepository
                .findByRoomIdAndUserIdAndLeftAtIsNull(roomId, requesterId)
                .orElseThrow(ChatNotParticipantException::new);

        List<ChatRoomParticipant> activeParticipants =
                chatRoomParticipantRepository.findAllByRoomIdAndLeftAtIsNull(roomId);
        String lastVisibleMessageId = saveSystemLeaveMessage(roomId, requesterId);

        requesterParticipant.leave(lastVisibleMessageId);

        if (chatRoom.getRoomType() == RoomType.DIRECT) {
            // 1:1 방은 한 명이 나가면 새 메시지를 막기 위해 방을 비활성화한다.
            chatRoom.inactivate(requesterId);
            return result(chatRoom, requesterParticipant);
        }

        leaveGroupRoom(chatRoom, requesterParticipant, activeParticipants, requesterId);
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
            return;
        }

        if (requesterParticipant.getRole() == ParticipantRole.OWNER) {
            // OWNER가 나가면 남은 참여자 중 가장 먼저 참여한 사용자에게 위임하고,
            // joinedAt이 같으면 userId 기준으로 결정해 항상 같은 결과가 나오게 한다.
            remainingParticipants.stream()
                    .min(Comparator.comparing(ChatRoomParticipant::getJoinedAt)
                            .thenComparing(ChatRoomParticipant::getUserId))
                    .ifPresent(ChatRoomParticipant::assignOwnerRole);
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
