package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.room.dto.command.ChatRoomInviteCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomInviteResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.application.room.port.ChatUserValidationPort;
import com.sparta.ditto.chat.domain.exception.ChatAlreadyParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatInviteForbiddenException;
import com.sparta.ditto.chat.domain.exception.ChatNotGroupRoomException;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoomInactiveException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.domain.room.RoomType;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomInviteService {

    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;
    private final ChatUserValidationPort chatUserValidationPort;

    @Transactional
    public ChatRoomInviteResult invite(ChatRoomInviteCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        UUID requesterId = command.requesterId();
        UUID roomId = command.roomId();

        ChatRoom chatRoom = chatRoomPort.findById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);
        if (chatRoom.getRoomType() != RoomType.GROUP) {
            throw new ChatNotGroupRoomException();
        }
        if (chatRoom.getStatus() != RoomStatus.ACTIVE) {
            throw new ChatRoomInactiveException();
        }

        // 요청자는 방의 활성 참여자이면서 OWNER여야 초대할 수 있다.
        ChatRoomParticipant requester = chatRoomParticipantPort
                .findActiveParticipant(roomId, requesterId)
                .orElseThrow(ChatNotParticipantException::new);
        if (requester.getRole() != ParticipantRole.OWNER) {
            throw new ChatInviteForbiddenException();
        }

        List<UUID> targetUserIds = resolveTargetUserIds(requesterId, command.targetUserIds());
        // 초대 대상 사용자 존재 여부와 요청자-대상 차단 관계를 user-service에서 검증한다.
        chatUserValidationPort.validateGroupChatParticipants(requesterId, targetUserIds);

        for (UUID targetUserId : targetUserIds) {
            inviteParticipant(roomId, targetUserId);
        }

        log.info("Chat room invited. requesterId={}, roomId={}, invitedCount={}",
                requesterId, roomId, targetUserIds.size());

        return ChatRoomInviteResult.of(roomId, targetUserIds);
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
            // 이미 활성 참여자인 사용자는 재초대할 수 없다.
            throw new ChatAlreadyParticipantException();
        }
        // 나간 사용자는 기존 row를 재사용해 재참여시킨다.
        participant.reInvite(ParticipantRole.MEMBER);
        chatRoomParticipantPort.save(participant);
    }

    private List<UUID> resolveTargetUserIds(UUID requesterId, List<UUID> targetUserIds) {
        Set<UUID> uniqueTargetIds = new LinkedHashSet<>();
        for (UUID targetUserId : targetUserIds) {
            if (targetUserId == null) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            // 요청자 본인은 이미 참여자이므로 초대 대상에서 제외한다.
            if (!targetUserId.equals(requesterId)) {
                uniqueTargetIds.add(targetUserId);
            }
        }
        if (uniqueTargetIds.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return List.copyOf(uniqueTargetIds);
    }
}
