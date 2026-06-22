package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.message.dto.result.ChatMessageVisibilityRange;
import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageVisibilityService {

    private final ChatParticipantValidator chatParticipantValidator;

    @Transactional(readOnly = true)
    public ChatMessageVisibilityRange getVisibilityRange(UUID roomId, UUID userId) {
        if (roomId == null || userId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // 나간 참여자도 과거 메시지는 조회할 수 있으므로 leftAt 조건 없는 참여자 조회를 사용한다.
        // 실제 MongoDB 조회 조건 적용은 메시지 조회 흐름에서 이 range를 받아 연결한다.
        ChatRoomParticipant participant = chatParticipantValidator.getParticipant(roomId, userId);
        if (participant.getLeftAt() == null) {
            return ChatMessageVisibilityRange.currentParticipant(
                    roomId,
                    userId,
                    participant.getJoinedAt()
            );
        }

        return ChatMessageVisibilityRange.leftParticipant(
                roomId,
                userId,
                participant.getJoinedAt(),
                participant.getLastVisibleMessageId()
        );
    }
}
