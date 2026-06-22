package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatReadCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatReadResult;
import com.sparta.ditto.chat.domain.exception.ChatMessageNotFoundException;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageDocument;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageMongoRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatReadService {

    private final ChatParticipantValidator chatParticipantValidator;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final ChatMessageMongoRepository chatMessageMongoRepository;

    @Transactional
    public ChatReadResult updateReadState(ChatReadCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        chatParticipantValidator.ensureRoomActive(command.roomId());

        ChatRoomParticipant participant = chatRoomParticipantRepository
                .findByRoomIdAndUserIdAndLeftAtIsNull(
                        command.roomId(),
                        command.requesterId()
                )
                .orElseThrow(ChatNotParticipantException::new);

        Instant readAt = Instant.now();
        if (shouldUpdateReadPosition(participant, command)) {
            participant.updateLastRead(command.lastReadMessageId(), readAt);
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
        ChatMessageDocument requestedMessage = findMessage(
                command.roomId(),
                command.lastReadMessageId()
        );
        String currentMessageId = participant.getLastReadMessageId();
        if (currentMessageId == null || currentMessageId.isBlank()) {
            return true;
        }

        ChatMessageDocument currentMessage = findMessage(command.roomId(), currentMessageId);
        return isAfter(currentMessage, requestedMessage);
    }

    private ChatMessageDocument findMessage(UUID roomId, String messageId) {
        return chatMessageMongoRepository.findByMessageIdAndRoomId(messageId, roomId)
                .orElseThrow(ChatMessageNotFoundException::new);
    }

    private boolean isAfter(
            ChatMessageDocument currentMessage,
            ChatMessageDocument requestedMessage
    ) {
        int createdAtCompare = requestedMessage.getCreatedAt()
                .compareTo(currentMessage.getCreatedAt());
        if (createdAtCompare != 0) {
            return createdAtCompare > 0;
        }
        return requestedMessage.getMessageId().compareTo(currentMessage.getMessageId()) > 0;
    }
}
