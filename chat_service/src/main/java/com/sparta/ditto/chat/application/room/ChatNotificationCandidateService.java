package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatNotificationCandidateService {

    private final ChatParticipantValidator chatParticipantValidator;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Transactional(readOnly = true)
    public List<UUID> findNotificationCandidateUserIds(UUID roomId, UUID senderId) {
        if (roomId == null || senderId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        chatParticipantValidator.ensureRoomActive(roomId);
        chatParticipantValidator.ensureActiveParticipant(roomId, senderId);

        // PostgreSQL 메타데이터 기준 1차 후보만 만든다.
        // Redis active_room 필터링과 Kafka 발행은 메시지 전송 흐름에서 처리한다.
        return chatRoomParticipantRepository.findAllByRoomIdAndLeftAtIsNull(roomId).stream()
                .filter(participant -> !participant.getUserId().equals(senderId))
                .filter(ChatRoomParticipant::isNotificationEnabled)
                .map(ChatRoomParticipant::getUserId)
                .toList();
    }
}
