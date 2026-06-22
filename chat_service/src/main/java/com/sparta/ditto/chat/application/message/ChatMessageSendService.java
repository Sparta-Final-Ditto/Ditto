package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.message.dto.ChatMessageSendCommand;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessageDedupStore;
import com.sparta.ditto.chat.application.message.port.ChatMessagePublisher;
import com.sparta.ditto.chat.application.message.port.DedupBeginResult;
import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.ChatRoomMetadataService;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.message.MessageIdGenerator;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageDocument;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageMongoRepository;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
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
    private final ChatMessageDedupStore chatMessageDedupStore;

    // 사용자 메시지 전송: 검증 → 중복 확인 → 저장 → last message 갱신 → ACK → 브로드캐스트
    public SentMessage sendUserMessage(ChatMessageSendCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        chatParticipantValidator.ensureActiveParticipant(command.roomId(), command.senderId());
        chatParticipantValidator.ensureRoomActive(command.roomId());
        validateUserContent(command);

        DedupBeginResult dedup = chatMessageDedupStore.begin(
                command.roomId(), command.senderId(), command.clientMessageId());

        return switch (dedup.status()) {
            case NEW -> saveAndPublish(command);
            case DUPLICATE_COMPLETED -> ackDuplicate(command, dedup.messageId());
            case DUPLICATE_PROCESSING ->
                    throw new BusinessException(ChatErrorCode.CHAT_DUPLICATE_PROCESSING);
        };
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

    private SentMessage saveAndPublish(ChatMessageSendCommand command) {
        ChatMessageDocument saved;
        try {
            String messageId = messageIdGenerator.generate();
            saved = chatMessageMongoRepository.save(
                    ChatMessageDocument.createUserMessage(
                            messageId,
                            command.roomId(),
                            command.senderId(),
                            command.clientMessageId(),
                            command.messageType(),
                            command.content()
                    )
            );
        } catch (RuntimeException ex) {
            // 저장 실패 → PROCESSING 잠금 해제해 즉시 재시도 가능하게 한다.
            chatMessageDedupStore.release(
                    command.roomId(), command.senderId(), command.clientMessageId());
            throw ex;
        }

        // 저장 성공 시점에 dedup을 확정한다.
        // 이후 단계가 실패해도 재시도는 기존 messageId ACK로 멱등 처리된다.
        chatMessageDedupStore.complete(
                command.roomId(), command.senderId(),
                command.clientMessageId(), saved.getMessageId());

        chatRoomMetadataService.updateLastMessage(
                command.roomId(), saved.getMessageId(), saved.getCreatedAt());

        SentMessage sentMessage = SentMessage.from(saved);
        chatMessagePublisher.ackToSender(command.senderId(), sentMessage);
        chatMessagePublisher.broadcast(command.roomId(), sentMessage);
        return sentMessage;
    }

    // 완료된 중복: 새 저장/브로드캐스트 없이 기존 messageId로 ACK만 반환
    private SentMessage ackDuplicate(ChatMessageSendCommand command, String messageId) {
        SentMessage sentMessage = chatMessageMongoRepository.findByMessageId(messageId)
                .map(SentMessage::from)
                .orElseGet(() -> duplicateSentMessage(command, messageId));
        chatMessagePublisher.ackToSender(command.senderId(), sentMessage);
        return sentMessage;
    }

    private SentMessage duplicateSentMessage(ChatMessageSendCommand command, String messageId) {
        return new SentMessage(
                messageId,
                command.roomId(),
                command.senderId(),
                null,
                command.clientMessageId(),
                command.messageType(),
                command.content(),
                Instant.now(),
                null
        );
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
