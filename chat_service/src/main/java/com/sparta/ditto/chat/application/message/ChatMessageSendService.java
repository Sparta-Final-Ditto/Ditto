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
    private final ChatMessageCommitService chatMessageCommitService;

    // 사용자 메시지 전송: 검증 → 중복 확인 → 저장 → last message 갱신 → ACK → 브로드캐스트
    public SentMessage sendUserMessage(ChatMessageSendCommand command) {
        long totalStartNanos = System.nanoTime();
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        chatParticipantValidator.ensureActiveParticipant(command.roomId(), command.senderId());
        chatParticipantValidator.ensureRoomActive(command.roomId());
        validateUserContent(command);
        long validationEndNanos = System.nanoTime();

        DedupBeginResult dedup = chatMessageDedupStore.begin(
                command.roomId(), command.senderId(), command.clientMessageId());
        long dedupBeginEndNanos = System.nanoTime();

        return switch (dedup.status()) {
            case NEW -> saveAndPublish(
                    command,
                    totalStartNanos,
                    validationEndNanos,
                    dedupBeginEndNanos);
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
        log.debug("System chat message saved and broadcast. "
                        + "roomId={}, actorId={}, messageId={}, messageType={}",
                roomId, actorId, saved.messageId(), messageType);
        return saved;
    }

    private SentMessage saveAndPublish(
            ChatMessageSendCommand command,
            long totalStartNanos,
            long validationEndNanos,
            long dedupBeginEndNanos
    ) {
        SentMessage saved;
        long saveStartNanos = System.nanoTime();
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
        long mongoSaveEndNanos = System.nanoTime();

        // 저장 성공 시점에 dedup을 확정한다.
        // 이후 단계가 실패해도 재시도는 기존 messageId ACK로 멱등 처리된다.
        chatMessageDedupStore.complete(
                command.roomId(), command.senderId(),
                command.clientMessageId(), saved.messageId());
        long dedupCompleteEndNanos = System.nanoTime();

        // 메타갱신(PostgreSQL) + 알림 이벤트 발행(예약)을 하나의 트랜잭션으로 처리한다.
        // 실제 알림 dispatch는 커밋 성공 후 AFTER_COMMIT 리스너가 별도 스레드로 수행한다.
        try {
            chatMessageCommitService.commitMetadataAndRegisterNotification(
                    command.roomId(), saved);
        } catch (RuntimeException ex) {
            // MongoDB 저장 성공 후 PostgreSQL lastMessage 갱신이 실패한 갭.
            // 본문은 MongoDB에 남아 유실은 아니고 미리보기만 어긋남 — 보정 대상이라 관측용으로 남긴다.
            log.error("메시지 저장 후 메타갱신/알림 예약 실패 — lastMessage 갭 발생(보정 대상). "
                            + "roomId={}, senderId={}, messageId={}",
                    command.roomId(), command.senderId(), saved.messageId(), ex);
            throw ex;
        }
        long commitEndNanos = System.nanoTime();

        chatMessagePublisher.ackToSender(command.senderId(), saved);
        long ackEndNanos = System.nanoTime();

        chatMessagePublisher.broadcast(command.roomId(), saved);
        long broadcastEndNanos = System.nanoTime();

        logSendPerformance(
                command,
                saved.messageId(),
                totalStartNanos,
                validationEndNanos,
                dedupBeginEndNanos,
                saveStartNanos,
                mongoSaveEndNanos,
                dedupCompleteEndNanos,
                commitEndNanos,
                ackEndNanos,
                broadcastEndNanos);

        log.debug("User chat message saved and published. "
                        + "roomId={}, senderId={}, clientMessageId={}, "
                        + "messageId={}, messageType={}",
                command.roomId(), command.senderId(), command.clientMessageId(),
                saved.messageId(), saved.messageType());
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
        log.debug("Duplicate chat message acknowledged with existing message. "
                        + "roomId={}, senderId={}, clientMessageId={}, messageId={}",
                command.roomId(), command.senderId(), command.clientMessageId(),
                original.messageId());
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

    // 성능 관측용 구간 로그(debug). 평소엔 미출력되고, 분석/장애 시 logging.level=DEBUG로 활성화한다.
    private void logSendPerformance(
            ChatMessageSendCommand command,
            String messageId,
            long totalStartNanos,
            long validationEndNanos,
            long dedupBeginEndNanos,
            long saveStartNanos,
            long mongoSaveEndNanos,
            long dedupCompleteEndNanos,
            long commitEndNanos,
            long ackEndNanos,
            long broadcastEndNanos
    ) {
        // commitMs = lastMessage 갱신 + 알림 이벤트 발행(예약)을 묶은 트랜잭션 구간.
        // 실제 알림 dispatch는 커밋 후 별도 스레드라 이 측정에 포함되지 않는다.
        log.debug("Chat message send perf. roomId={}, senderId={}, messageId={}, "
                        + "validationMs={}, dedupBeginMs={}, mongoSaveMs={}, "
                        + "dedupCompleteMs={}, commitMs={}, ackMs={}, "
                        + "broadcastCallMs={}, totalMs={}",
                command.roomId(),
                command.senderId(),
                messageId,
                elapsedMs(totalStartNanos, validationEndNanos),
                elapsedMs(validationEndNanos, dedupBeginEndNanos),
                elapsedMs(saveStartNanos, mongoSaveEndNanos),
                elapsedMs(mongoSaveEndNanos, dedupCompleteEndNanos),
                elapsedMs(dedupCompleteEndNanos, commitEndNanos),
                elapsedMs(commitEndNanos, ackEndNanos),
                elapsedMs(ackEndNanos, broadcastEndNanos),
                elapsedMs(totalStartNanos, broadcastEndNanos));
    }

    private long elapsedMs(long startNanos, long endNanos) {
        return (endNanos - startNanos) / 1_000_000;
    }
}
