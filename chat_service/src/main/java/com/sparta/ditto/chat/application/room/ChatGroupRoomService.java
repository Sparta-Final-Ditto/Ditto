package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.room.dto.command.ChatGroupRoomCreateCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatGroupRoomResult;
import com.sparta.ditto.chat.application.room.port.ChatSenderProfile;
import com.sparta.ditto.chat.application.room.port.ChatUserProfilePort;
import com.sparta.ditto.chat.application.room.port.ChatUserValidationPort;
import com.sparta.ditto.chat.domain.exception.ChatInvalidGroupParticipantsException;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
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
public class ChatGroupRoomService {

    private static final String UNKNOWN_NICKNAME = "알 수 없는 사용자";

    private final ChatUserValidationPort chatUserValidationPort;
    private final ChatUserProfilePort chatUserProfilePort;
    private final ChatGroupRoomRegistrar groupRoomRegistrar;

    // user-service 호출(검증·닉네임 조회)은 트랜잭션 밖에서 먼저 끝내고,
    // DB 저장만 registrar의 짧은 트랜잭션으로 묶는다. (초대 경로와 동일 원칙)
    public ChatGroupRoomResult createGroupRoom(ChatGroupRoomCreateCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        UUID requesterId = command.requesterId();
        List<UUID> memberUserIds = resolveMemberUserIds(
                requesterId,
                command.participantUserIds()
        );

        chatUserValidationPort.validateGroupChatParticipants(requesterId, memberUserIds);
        String creatorNickname = resolveNickname(requesterId);

        return groupRoomRegistrar.create(
                command.roomName(), requesterId, memberUserIds, creatorNickname);
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

    private String resolveNickname(UUID userId) {
        ChatSenderProfile profile = chatUserProfilePort.findProfile(userId);
        String nickname = profile.nickname();
        return (nickname == null || nickname.isBlank()) ? UNKNOWN_NICKNAME : nickname;
    }
}
