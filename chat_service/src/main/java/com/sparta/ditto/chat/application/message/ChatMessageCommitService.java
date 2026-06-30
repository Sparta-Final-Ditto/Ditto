package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.event.ChatMessageNotificationRequestedEvent;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.ChatRoomMetadataService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 메시지 저장 성공 후, PostgreSQL lastMessage 갱신과 알림 이벤트 발행을
 * 하나의 트랜잭션 경계로 묶는다.
 *
 * <p>알림 이벤트는 이 트랜잭션 안에서 "발행(예약)"만 한다. 실제 알림 dispatch는
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 리스너가 커밋 성공 후 별도 스레드에서 처리한다.
 * 그래서 무거운 알림 작업이 트랜잭션 시간을 늘리지 않는다.
 *
 * <p>{@code ChatMessageSendService}와 분리된 별도 빈이라, self-invocation 없이
 * {@code @Transactional} 프록시가 정상 적용된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageCommitService {

    private final ChatRoomMetadataService chatRoomMetadataService;
    private final ApplicationEventPublisher applicationEventPublisher;

    // 알림 발행 토글(기본 on). 알림 경로 장애 시 차단 / 성능 측정 시 알림 제외용.
    @Value("${chat.notification.dispatch-enabled:true}")
    private boolean notificationDispatchEnabled;

    @Transactional
    public void commitMetadataAndRegisterNotification(UUID roomId, SentMessage saved) {
        // PostgreSQL lastMessage 갱신 (updateLastMessage도 @Transactional이라 이 트랜잭션에 참여)
        chatRoomMetadataService.updateLastMessage(
                roomId, saved.messageId(), saved.createdAt());

        if (!notificationDispatchEnabled) {
            log.debug("Chat notification request skipped by config. roomId={}, messageId={}",
                    roomId, saved.messageId());
            return;
        }

        // 트랜잭션 안에서 "발행(예약)"만 한다. 커밋 성공 후 AFTER_COMMIT 리스너가 실제 dispatch.
        applicationEventPublisher.publishEvent(new ChatMessageNotificationRequestedEvent(saved));
    }
}
