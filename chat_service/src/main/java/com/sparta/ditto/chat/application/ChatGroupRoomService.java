package com.sparta.ditto.chat.application;

import com.sparta.ditto.chat.application.dto.ChatGroupRoomCreateCommand;
import com.sparta.ditto.chat.application.dto.ChatGroupRoomResult;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatGroupRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Transactional
    public ChatGroupRoomResult createGroupRoom(ChatGroupRoomCreateCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        UUID requesterId = command.requesterId();
        List<UUID> memberUserIds = resolveMemberUserIds(
                requesterId,
                command.participantUserIds()
        );

        ChatRoom chatRoom = chatRoomRepository.save(
                ChatRoom.createGroup(command.roomName())
        );

        // 그룹방 생성자는 OWNER, 나머지 참여자는 MEMBER로 저장한다.
        List<ChatRoomParticipant> participants = new ArrayList<>();
        participants.add(ChatRoomParticipant.join(
                chatRoom.getId(),
                requesterId,
                ParticipantRole.OWNER
        ));
        memberUserIds.forEach(userId -> participants.add(ChatRoomParticipant.join(
                chatRoom.getId(),
                userId,
                ParticipantRole.MEMBER
        )));
        chatRoomParticipantRepository.saveAll(participants);

        return ChatGroupRoomResult.of(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getRoomName(),
                chatRoom.getStatus()
        );
    }

    private List<UUID> resolveMemberUserIds(UUID requesterId, List<UUID> participantUserIds) {
        if (requesterId == null || participantUserIds == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        Set<UUID> uniqueMemberIds = new LinkedHashSet<>();
        for (UUID participantUserId : participantUserIds) {
            if (participantUserId == null) {
                throw new BusinessException(CommonErrorCode.INVALID_INPUT);
            }
            // 요청자는 OWNER로 별도 저장하므로 MEMBER 목록에서는 제외한다.
            if (!participantUserId.equals(requesterId)) {
                uniqueMemberIds.add(participantUserId);
            }
        }

        if (uniqueMemberIds.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return List.copyOf(uniqueMemberIds);
    }
}
