package com.sparta.ditto.feed.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.application.port.OutboxEventPublisher;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * {@link OutboxEventPublisher}의 Kafka 구현체.
 *
 * <p>OutboxEvent의 payload(JSONB 문자열)를 파싱하여 {@link Envelope}으로 래핑한 뒤
 * 동기 방식({@code .get()})으로 Kafka에 전송한다. 전송 실패 시 RuntimeException을 던져
 * 스케줄러의 재시도 카운트 증가 로직이 동작하도록 한다.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPublisherImpl implements OutboxEventPublisher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * OutboxEvent를 Kafka topic에 동기 발행한다.
     *
     * @param event 발행할 OutboxEvent
     * @throws RuntimeException JSON 직렬화 또는 Kafka 전송 실패 시
     */
    @Override
    public void publish(OutboxEvent event) {
        try {
            JsonNode payloadNode = OBJECT_MAPPER.readTree(event.getPayload());
            String message = OBJECT_MAPPER.writeValueAsString(new Envelope(
                    event.getId().toString(),
                    event.getEventType(),
                    event.getCreatedAt().toString(),
                    payloadNode
            ));
            kafkaTemplate.send(event.getTopic(), event.getAggregateId().toString(), message).get();
        } catch (Exception e) {
            throw new RuntimeException("Kafka 발행 실패: topic=" + event.getTopic(), e);
        }
    }

    /**
     * Kafka 메시지 본문 구조.
     *
     * @param eventId    OutboxEvent ID (UUID 문자열)
     * @param eventType  이벤트 유형 식별자
     * @param occurredAt 이벤트 발생 시각 (ISO-8601)
     * @param payload    이벤트 본문 데이터
     */
    private record Envelope(
            String eventId, String eventType, String occurredAt, JsonNode payload) {}
}
