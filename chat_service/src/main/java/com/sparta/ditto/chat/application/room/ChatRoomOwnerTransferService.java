package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.room.dto.result.ChatRoomOwnerTransferResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatNotGroupRoomException;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoleChangeForbiddenException;
import com.sparta.ditto.chat.domain.exception.ChatRoomInactiveException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.exception.ChatUnsupportedRoleChangeException;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomOwnerTransferService {

    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;

    @Transactional
    public ChatRoomOwnerTransferResult transferOwner(
            UUID requesterId, UUID roomId, UUID targetUserId, ParticipantRole role) {
        if (requesterId == null || roomId == null || targetUserId == null || role == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        // 현재는 OWNER 위임만 지원
        if (role != ParticipantRole.OWNER) {
            throw new ChatUnsupportedRoleChangeException();
        }
        if (requesterId.equals(targetUserId)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        ChatRoom chatRoom = chatRoomPort.findById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);
        if (chatRoom.getRoomType() != RoomType.GROUP) {
            throw new ChatNotGroupRoomException();
        }
        if (chatRoom.getStatus() != RoomStatus.ACTIVE) {
            throw new ChatRoomInactiveException();
        }

        ChatRoomParticipant requester = chatRoomParticipantPort
                .findActiveParticipant(roomId, requesterId)
                .orElseThrow(ChatNotParticipantException::new);
        if (requester.getRole() != ParticipantRole.OWNER) {
            throw new ChatRoleChangeForbiddenException();
        }

        ChatRoomParticipant target = chatRoomParticipantPort
                .findActiveParticipant(roomId, targetUserId)
                .orElseThrow(ChatNotParticipantException::new);

        // 기존 OWNER는 MEMBER로 강등하고 대상에게 OWNER를 위임
        requester.assignMemberRole();
        target.assignOwnerRole();

        log.info("Chat room owner transferred. roomId={}, previousOwnerId={}, newOwnerId={}",
                roomId, requesterId, targetUserId);

        return ChatRoomOwnerTransferResult.of(roomId, targetUserId, requesterId);
    }
}
