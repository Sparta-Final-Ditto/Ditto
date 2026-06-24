package com.sparta.ditto.feed.application.port;

import com.sparta.ditto.feed.domain.entity.OutboxEvent;

/**
 * Transactional Outbox 패턴에서 OutboxEvent를 외부 메시지 브로커에 발행하는 포트 인터페이스.
 *
 * <p>Application 계층이 선언하며, Infrastructure 계층(Kafka 등)이 구현한다.
 */
public interface OutboxEventPublisher {

    /**
     * OutboxEvent를 해당 이벤트의 topic으로 발행한다.
     *
     * @param event 발행할 OutboxEvent (topic, eventType, payload 포함)
     * @throws RuntimeException 브로커 전송 실패 시
     */
    void publish(OutboxEvent event);
}
