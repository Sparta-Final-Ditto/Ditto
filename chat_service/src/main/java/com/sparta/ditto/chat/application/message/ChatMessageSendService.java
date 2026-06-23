package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.message.dto.ChatMessageSendCommand;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessageCommandPort;
import com.sparta.ditto.chat.application.message.port.ChatMessageDedupStore;
import com.sparta.ditto.chat.application.message.port.ChatMessagePublisher;
import com.sparta.ditto.chat.application.message.port.ChatMessageQueryPort;
import com.sparta.ditto.chat.application.message.port.DedupBeginResult;
import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.ChatRoomMetadataService;
import com.sparta.ditto.chat.domain.exception.ChatDuplicateProcessingException;
import com.sparta.ditto.chat.domain.message.MessageIdGenerator;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private final ChatMessageCommandPort chatMessageCommandPort;
    private final ChatMessageQueryPort chatMessageQueryPort;
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
                    throw new ChatDuplicateProcessingException();
        };
    }

    // 시스템 메시지 저장 + 브로드캐스트
    public SentMessage saveSystemMessage(
            UUID roomId, UUID actorId, MessageType messageType, String content) {
        String messageId = messageIdGenerator.generate();
        SentMessage saved = chatMessageCommandPort.saveSystemMessage(
                messageId, roomId, actorId, messageType, content);

        chatRoomMetadataService.updateLastMessage(
                roomId, saved.messageId(), saved.createdAt());

        chatMessagePublisher.broadcast(roomId, saved);
        return saved;
    }

    private SentMessage saveAndPublish(ChatMessageSendCommand command) {
        SentMessage saved;
        try {
            String messageId = messageIdGenerator.generate();
            saved = chatMessageCommandPort.saveUserMessage(
                    messageId,
                    command.roomId(),
                    command.senderId(),
                    command.clientMessageId(),
                    command.messageType(),
                    command.content()
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
                command.clientMessageId(), saved.messageId());

        chatRoomMetadataService.updateLastMessage(
                command.roomId(), saved.messageId(), saved.createdAt());

        chatMessagePublisher.ackToSender(command.senderId(), saved);
        chatMessagePublisher.broadcast(command.roomId(), saved);
        return saved;
    }

    private SentMessage ackDuplicate(ChatMessageSendCommand command, String messageId) {
        SentMessage original = chatMessageQueryPort.findByMessageId(messageId)
                .orElse(null);
        if (original == null) {
            log.error("Dedup completed but original message missing. "
                            + "roomId={}, senderId={}, clientMessageId={}, messageId={}",
                    command.roomId(), command.senderId(), command.clientMessageId(), messageId);
            // 잘못된 dedup 키를 정리해 재시도가 새로 저장되도록 유도
            chatMessageDedupStore.release(
                    command.roomId(), command.senderId(), command.clientMessageId());
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }

        chatMessagePublisher.ackToSender(command.senderId(), original);
        return original;
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
