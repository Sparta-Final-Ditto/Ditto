package com.sparta.ditto.notification.infrastructure.kafka;

import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.infrastructure.persistence.NotificationJpaRepository;
import com.sparta.ditto.notification.support.AbstractPostgresContainerTest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DLT 스모크(EmbeddedKafka 1개): 파싱 불가 메시지가 DLT로 라우팅되고(원본토픽·예외 헤더 보존),
 * 직후 발행한 정상 메시지는 계속 처리(offset 진행)됨을 확인한다.
 * 프레임워크 동작(재시도 횟수/backoff/recoverer 호출 등)이 아니라 "우리가 정한 설정 결정"만 검증한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EmbeddedKafka(partitions = 1, topics = {
        "post-events",
        "chat-message-created",
        "post-events.notification-service.DLT"
})
@DisplayName("DLT 스모크 - 파싱 불가 → DLT 발행(헤더 보존) + 직후 정상 메시지 처리")
class NotificationDltSmokeTest extends AbstractPostgresContainerTest {

    private static final String TOPIC = "post-events";
    private static final String DLT_TOPIC = "post-events.notification-service.DLT";
    private static final String LIKE_ID = "like-dlt-smoke-1";
    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final String VALID_LIKED_JSON = """
            {"eventId":"e1","eventType":"POST_LIKED","occurredAt":"2026-07-06T00:00:00Z",
             "payload":{"postId":"11111111-1111-1111-1111-111111111111","likeId":"like-dlt-smoke-1",
             "userId":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","actorNickname":"스모크",
             "ownerId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","likedAt":"2026-07-06T00:00:00Z"}}
            """;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        // notification은 redis를 코드에서 쓰지 않지만 base yml의 ${REDIS_HOST} 플레이스홀더 해소용(연결은 lazy)
        registry.add("spring.data.redis.host", () -> "localhost");
        // EmbeddedKafka는 매번 비어 있으므로 처음부터 소비
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationJpaRepository jpaRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @AfterEach
    void cleanup() {
        jpaRepository.deleteAll();
    }

    @Test
    @DisplayName("파싱 불가 메시지는 post-events.notification-service.DLT로 발행(원본토픽·예외 헤더 존재)되고, 직후 정상 메시지는 저장된다")
    void invalidMessage_goesToDlt_andValidMessageStillProcessed() {
        // When: 파싱 불가 메시지 → 정상 메시지 순서로 발행 (동일 파티션, 순서 보존)
        kafkaTemplate.send(TOPIC, "{not-valid-json");
        kafkaTemplate.send(TOPIC, VALID_LIKED_JSON);

        // Then 1: DLT에서 파싱 불가 메시지 1건 수신 + 헤더 보존
        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("dlt-verify-group", "true", embeddedKafka);
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        try (Consumer<String, String> dltConsumer =
                     new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()) {
            embeddedKafka.consumeFromAnEmbeddedTopic(dltConsumer, DLT_TOPIC);
            ConsumerRecord<String, String> dltRecord =
                    KafkaTestUtils.getSingleRecord(dltConsumer, DLT_TOPIC, Duration.ofSeconds(15));

            assertThat(dltRecord.value()).isEqualTo("{not-valid-json");
            assertThat(dltRecord.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC)).isNotNull();
            assertThat(new String(dltRecord.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_TOPIC).value()))
                    .isEqualTo(TOPIC);
            assertThat(dltRecord.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotNull();
        }

        // Then 2: 파싱 불가 메시지가 소비를 막지 않고 직후 정상 메시지가 저장됨(offset 진행)
        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(jpaRepository.existsByTypeAndTargetIdAndReceiverId(
                        NotificationType.LIKE, LIKE_ID, OWNER_ID)).isTrue());
    }
}