package com.sparta.ditto.chat.infrastructure.kafka;

import com.sparta.ditto.chat.application.event.ChatMessageCreatedEvent;
import com.sparta.ditto.chat.application.event.ChatNotificationEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatNotificationEventKafkaPublisher implements ChatNotificationEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public ChatNotificationEventKafkaPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${kafka.topic.chat-message-created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(ChatMessageCreatedEvent event) {
        // key는 roomId로 두어 같은 방 이벤트의 파티션 순서를 보존한다.
        kafkaTemplate.send(topic, event.roomId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // 알림 발행 실패는 채팅 전송을 막지 않는다. 로그만 남긴다.
                        log.error("chat-message-created 발행 실패. roomId={}, messageId={}",
                                event.roomId(), event.messageId(), ex);
                    } else {
                        log.debug("알림 발행 완료. roomId={}, messageId={}, receiverCount={}",
                                event.roomId(), event.messageId(), event.receiverIds().size());
                    }
                });
    }
}
