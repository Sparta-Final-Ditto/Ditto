package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatReadCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatReadResult;
import com.sparta.ditto.chat.application.room.port.ChatReadMessagePort;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.domain.exception.ChatMessageNotFoundException;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatReadService {

    private final ChatParticipantValidator chatParticipantValidator;
    private final ChatRoomParticipantPort chatRoomParticipantPort;
    private final ChatReadMessagePort chatReadMessagePort;

    @Transactional
    public ChatReadResult updateReadState(ChatReadCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        chatParticipantValidator.ensureRoomActive(command.roomId());

        // 같은 사용자의 동시 읽음 요청이 lastRead를 뒤로 되돌리지 않도록,
        // participant row에 쓰기 락을 걸어 읽음 처리를 직렬화한다.
        ChatRoomParticipant participant = chatRoomParticipantPort
                .findActiveParticipantForUpdate(
                        command.roomId(),
                        command.requesterId()
                )
                .orElseThrow(ChatNotParticipantException::new);

        Instant readAt = Instant.now();
        if (shouldUpdateReadPosition(participant, command)) {
            // 읽은 위치 갱신과 unread_count 초기화를 하나의 atomic update로 처리한다.
            // 엔티티 dirty-checking으로 하면 조회 시점 스냅샷을 기준으로 써버려,
            // 그 사이 도착한 메시지가 올린 unread 증가를 덮어쓰는 lost update가 생긴다.
            chatRoomParticipantPort.markReadAndResetUnread(
                    command.roomId(),
                    command.requesterId(),
                    command.lastReadMessageId(),
                    readAt
            );
            log.debug("Chat read position updated. userId={}, roomId={}, messageId={}",
                    command.requesterId(), command.roomId(), command.lastReadMessageId());
            return ChatReadResult.of(command.roomId(), command.lastReadMessageId(), readAt);
        }

        // 오래된 읽음 요청: 이미 더 최신 위치를 읽은 상태이므로 위치도 unread도 건드리지 않는다.
        log.debug("Chat read position ignored because requested message is not newer. "
                        + "userId={}, roomId={}, requestedMessageId={}, currentMessageId={}",
                command.requesterId(), command.roomId(),
                command.lastReadMessageId(), participant.getLastReadMessageId());
        return ChatReadResult.of(
                command.roomId(),
                participant.getLastReadMessageId(),
                participant.getLastReadAt()
        );
    }

    private boolean shouldUpdateReadPosition(
            ChatRoomParticipant participant,
            ChatReadCommand command
    ) {
        ChatReadMessagePort.ReadMessage requestedMessage = findMessage(
                command.roomId(),
                command.lastReadMessageId()
        );
        String currentMessageId = participant.getLastReadMessageId();
        if (currentMessageId == null || currentMessageId.isBlank()) {
            return true;
        }

        ChatReadMessagePort.ReadMessage currentMessage = findMessage(
                command.roomId(),
                currentMessageId
        );
        return isAfter(currentMessage, requestedMessage);
    }

    private ChatReadMessagePort.ReadMessage findMessage(UUID roomId, String messageId) {
        return chatReadMessagePort.findReadMessage(roomId, messageId)
                .orElseThrow(ChatMessageNotFoundException::new);
    }

    private boolean isAfter(
            ChatReadMessagePort.ReadMessage currentMessage,
            ChatReadMessagePort.ReadMessage requestedMessage
    ) {
        int createdAtCompare = requestedMessage.createdAt()
                .compareTo(currentMessage.createdAt());
        if (createdAtCompare != 0) {
            return createdAtCompare > 0;
        }
        return requestedMessage.messageId().compareTo(currentMessage.messageId()) > 0;
    }
}
