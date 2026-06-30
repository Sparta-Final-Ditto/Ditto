package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.event.ChatMessageNotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageNotificationEventListener {

    private final ChatMessageNotificationService chatMessageNotificationService;

    // 메시지 메타데이터 트랜잭션이 "커밋 성공"한 뒤에만(AFTER_COMMIT) 알림을 dispatch한다.
    // (롤백되면 알림이 나가지 않는다.) @Async라 별도 스레드에서 처리해 응답 경로를 막지 않는다.
    @Async("chatNotificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ChatMessageNotificationRequestedEvent event) {
        try {
            chatMessageNotificationService.dispatch(event.message());
        } catch (RuntimeException ex) {
            log.error("Chat notification dispatch failed. roomId={}, messageId={}",
                    event.message().roomId(), event.message().messageId(), ex);
        }
    }
}
