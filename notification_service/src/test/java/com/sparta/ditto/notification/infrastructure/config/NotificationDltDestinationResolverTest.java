package com.sparta.ditto.notification.infrastructure.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationDltDestinationResolver - 실패 레코드를 <원본토픽>.<그룹>.DLT(파티션 미지정)로 라우팅")
class NotificationDltDestinationResolverTest {

    private static final String GROUP = "notification-service";
    private final NotificationDltDestinationResolver resolver =
            new NotificationDltDestinationResolver(GROUP);

    @Test
    @DisplayName("post-events 레코드 → post-events.notification-service.DLT, 파티션 미지정(-1)")
    void resolve_postEvents_toDltTopicWithUnspecifiedPartition() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("post-events", 3, 100L, "key", "value");

        TopicPartition tp = resolver.apply(record, new RuntimeException("boom"));

        assertThat(tp.topic()).isEqualTo("post-events.notification-service.DLT");
        assertThat(tp.partition()).isEqualTo(-1); // 원본 파티션(3)을 따르지 않고 미지정
    }

    @Test
    @DisplayName("chat-message-created 레코드 → chat-message-created.notification-service.DLT, 파티션 미지정(-1)")
    void resolve_chatMessageCreated_toDltTopicWithUnspecifiedPartition() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("chat-message-created", 0, 5L, "key", "value");

        TopicPartition tp = resolver.apply(record, new RuntimeException("boom"));

        assertThat(tp.topic()).isEqualTo("chat-message-created.notification-service.DLT");
        assertThat(tp.partition()).isEqualTo(-1);
    }
}