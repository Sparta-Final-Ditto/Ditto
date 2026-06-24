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

        ChatRoomParticipant participant = chatRoomParticipantPort
                .findActiveParticipant(
                        command.roomId(),
                        command.requesterId()
                )
                .orElseThrow(ChatNotParticipantException::new);

        Instant readAt = Instant.now();
        if (shouldUpdateReadPosition(participant, command)) {
            participant.updateLastRead(command.lastReadMessageId(), readAt);
            log.debug("Chat read position updated. userId={}, roomId={}, messageId={}",
                    command.requesterId(), command.roomId(), command.lastReadMessageId());
        } else {
            log.debug("Chat read position ignored because requested message is not newer. "
                            + "userId={}, roomId={}, requestedMessageId={}, currentMessageId={}",
                    command.requesterId(), command.roomId(),
                    command.lastReadMessageId(), participant.getLastReadMessageId());
        }

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
