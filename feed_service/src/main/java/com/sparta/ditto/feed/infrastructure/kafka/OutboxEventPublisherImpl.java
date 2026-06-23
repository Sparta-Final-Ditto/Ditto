package com.sparta.ditto.feed.infrastructure.kafka;

import com.sparta.ditto.feed.application.UploadUrlResult.port.out.OutboxEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisherImpl implements OutboxEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public void publish(String topic, String payload) {
        try {
            kafkaTemplate.send(topic, payload).get();
        } catch (Exception e) {
            throw new RuntimeException("Kafka 발행 실패: topic=" + topic, e);
        }
    }
}
