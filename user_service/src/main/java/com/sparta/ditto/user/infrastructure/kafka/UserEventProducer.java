package com.sparta.ditto.user.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.user-registered}")
    private String userRegisteredTopic;

    public void sendUserCreated(UserCreatedEvent event) {
        kafkaTemplate.send(userRegisteredTopic, event.userId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("USER_CREATED 이벤트 발행 실패. userId={}", event.userId(), ex);
                    }
                });
    }

    public void sendUserInterestsRegistered(UserInterestsRegisteredEvent event) {
        kafkaTemplate.send(userRegisteredTopic, event.userId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("USER_INTERESTS_REGISTERED 이벤트 발행 실패. userId={}",
                                event.userId(), ex);
                    }
                });
    }
}
