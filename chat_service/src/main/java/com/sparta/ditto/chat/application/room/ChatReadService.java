package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatReadCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatReadResult;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatReadService {

    private final ChatParticipantValidator chatParticipantValidator;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Transactional
    public ChatReadResult updateReadState(ChatReadCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        chatParticipantValidator.ensureRoomActive(command.roomId());

        // 읽음 처리는 현재 참여자만 가능하므로 leftAt이 null인 참여자 row를 직접 조회한다.
        ChatRoomParticipant participant = chatRoomParticipantRepository
                .findByRoomIdAndUserIdAndLeftAtIsNull(
                        command.roomId(),
                        command.requesterId()
                )
                .orElseThrow(ChatNotParticipantException::new);

        Instant readAt = Instant.now();
        // TODO: B 메시지 조회 계약과 연결되면 createdAt + messageId 기준으로
        // 이전 읽음 위치로 되돌아가지 않도록 방어한다.
        participant.updateLastRead(command.lastReadMessageId(), readAt);

        return ChatReadResult.of(
                command.roomId(),
                participant.getLastReadMessageId(),
                participant.getLastReadAt()
        );
    }
}
