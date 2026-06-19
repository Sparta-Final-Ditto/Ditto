package com.sparta.ditto.chat.application.participant;

import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.common.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// TODO: ChatParticipantValidator 구현 머지 시 임시 구현을 교체/제거한다.
@Component
@RequiredArgsConstructor
public class ChatParticipantValidatorImpl implements ChatParticipantValidator {

    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Override
    @Transactional(readOnly = true)
    public void ensureActiveParticipant(UUID roomId, UUID userId) {
        chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }
}
