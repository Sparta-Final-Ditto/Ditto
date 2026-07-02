package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.event.ChatSystemMessageBroadcastRequestedEvent;
import com.sparta.ditto.chat.application.message.port.ChatMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSystemMessageBroadcastEventListener {

    private final ChatMessagePublisher chatMessagePublisher;

    // 시스템 메시지 broadcast는 저장 트랜잭션이 커밋 성공한 뒤에만 수행
    // 트랜잭션이 없으면 즉시 발행 (롤백되면 클라이언트가 메시지를 받지 않음)
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void handle(ChatSystemMessageBroadcastRequestedEvent event) {
        try {
            chatMessagePublisher.broadcast(event.roomId(), event.message());
        } catch (RuntimeException ex) {
            log.warn("System message broadcast failed. roomId={}, messageId={}",
                    event.roomId(), event.message().messageId(), ex);
        }
    }
}
