package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.room.dto.command.ChatGroupRoomCreateCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatGroupRoomResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatInvalidGroupParticipantsException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
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
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatGroupRoomService {

    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;

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

        ChatRoom chatRoom = chatRoomPort.save(
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
        chatRoomParticipantPort.saveAll(participants);

        log.info("Group chat room created. requesterId={}, roomId={}, participantCount={}",
                requesterId, chatRoom.getId(), participants.size());

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

        if (uniqueMemberIds.size() < 2) {
            throw new ChatInvalidGroupParticipantsException();
        }
        return List.copyOf(uniqueMemberIds);
    }
}
