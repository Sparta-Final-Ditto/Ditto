package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.room.dto.command.ChatRoomInviteCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomInviteResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.application.room.port.ChatSenderProfile;
import com.sparta.ditto.chat.application.room.port.ChatUserProfilePort;
import com.sparta.ditto.chat.application.room.port.ChatUserValidationPort;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomInviteService {

    private static final String UNKNOWN_NICKNAME = "알 수 없는 사용자";

    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;
    private final ChatUserValidationPort chatUserValidationPort;
    private final ChatRoomParticipantInviteRegistrar inviteRegistrar;
    private final ChatUserProfilePort chatUserProfilePort;

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
        // 초대 대상 존재/차단 관계 검증은 트랜잭션 밖에서 먼저 끝낸다.
        chatUserValidationPort.validateGroupChatParticipants(requesterId, targetUserIds);

        // 닉네임 조회도 트랜잭션 밖에서 끝내고 저장은 registrar 트랜잭션에 위임
        List<InvitedTarget> invitedTargets = new ArrayList<>();
        for (UUID targetUserId : targetUserIds) {
            invitedTargets.add(new InvitedTarget(targetUserId, resolveNickname(targetUserId)));
        }

        inviteRegistrar.register(chatRoom, invitedTargets);;

        log.info("Chat room invited. requesterId={}, roomId={}, invitedCount={}",
                requesterId, roomId, targetUserIds.size());
        return ChatRoomInviteResult.of(roomId, targetUserIds);
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

    private String resolveNickname(UUID userId) {
        ChatSenderProfile profile = chatUserProfilePort.findProfile(userId);
        String nickname = profile.nickname();
        return (nickname == null || nickname.isBlank()) ? UNKNOWN_NICKNAME : nickname;
    }
}
