package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.message.dto.ChatMessageSendCommand;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessagePublisher;
import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.ChatRoomMetadataService;
import com.sparta.ditto.chat.domain.message.MessageIdGenerator;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageDocument;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageMongoRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private final ChatMessageMongoRepository chatMessageMongoRepository;
    private final ChatParticipantValidator chatParticipantValidator;
    private final MessageIdGenerator messageIdGenerator;
    private final ChatRoomMetadataService chatRoomMetadataService;
    private final ChatMessagePublisher chatMessagePublisher;

    // 사용자 메시지 전송: 검증 → 저장 → last message 갱신 → ACK → 브로드캐스트
    public SentMessage sendUserMessage(ChatMessageSendCommand command) {
        chatParticipantValidator.ensureActiveParticipant(command.roomId(), command.senderId());
        chatParticipantValidator.ensureRoomActive(command.roomId());
        validateUserContent(command);

        String messageId = messageIdGenerator.generate();
        ChatMessageDocument saved = chatMessageMongoRepository.save(
                ChatMessageDocument.createUserMessage(
                        messageId,
                        command.roomId(),
                        command.senderId(),
                        command.clientMessageId(),
                        command.messageType(),
                        command.content()
                )
        );

        chatRoomMetadataService.updateLastMessage(
                command.roomId(), saved.getMessageId(), saved.getCreatedAt());

        SentMessage sentMessage = SentMessage.from(saved);
        // ACK(발신자) 먼저 그 다음 브로드캐스트
        chatMessagePublisher.ackToSender(command.senderId(), sentMessage);
        chatMessagePublisher.broadcast(command.roomId(), sentMessage);
        return sentMessage;
    }

    // 시스템 메시지 저장 + 브로드캐스트
    public SentMessage saveSystemMessage(
            UUID roomId, UUID actorId, MessageType messageType, String content) {
        String messageId = messageIdGenerator.generate();
        ChatMessageDocument saved = chatMessageMongoRepository.save(
                ChatMessageDocument.createSystemMessage(
                        messageId, roomId, actorId, messageType, content));

        chatRoomMetadataService.updateLastMessage(
                roomId, saved.getMessageId(), saved.getCreatedAt());

        SentMessage sentMessage = SentMessage.from(saved);
        chatMessagePublisher.broadcast(roomId, sentMessage);
        return sentMessage;
    }

    private void validateUserContent(ChatMessageSendCommand command) {
        // TEXT: 본문 문자열 / IMAGE: URL·metadata 문자열
        if (command.messageType() == null
                || command.clientMessageId() == null
                || command.content() == null
                || command.content().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (command.messageType().isSystem()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
