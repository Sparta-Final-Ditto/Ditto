package com.sparta.ditto.notification.infrastructure.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Consumer 실패 정책 (TRD 10장):
 * <ul>
 *   <li>일시적 오류는 총 3회(최초 1회 + 재시도 2회, FixedBackOff 1초) 시도 후 DLT로 발행하고 offset을 커밋한다.</li>
 *   <li>DLT 토픽은 "&lt;원본토픽&gt;.&lt;컨슈머그룹&gt;.DLT"로 라우팅한다(post-events는 다른 컨슈머 그룹도 구독하므로
 *       Spring 기본 "&lt;토픽&gt;.DLT"를 쓰면 서비스 간 실패 메시지가 섞인다). 파티션은 미지정한다.</li>
 *   <li>not-retryable 예외(JsonProcessingException/IllegalArgumentException)는 재시도 없이 즉시 DLT로 보낸다.</li>
 *   <li>DataIntegrityViolationException(멱등 성공)은 재시도·DLT 모두 없이 로그 후 정상 종료한다.</li>
 *   <li>DLT 발행 시 원본 topic/partition/offset·예외를 Kafka 헤더로 보존하고, 발행 전 ERROR 로그에 스택트레이스를 남긴다.</li>
 * </ul>
 * consumer record value가 String이므로 DLT 재발행 producer도 StringSerializer로 고정한다(JsonSerializer는 이중 인코딩).
 */
@Configuration
public class KafkaConsumerConfig {

    private static final long RETENTION_14_DAYS_MS = Duration.ofDays(14).toMillis();

    /** DLT 토픽명 접미사(&lt;토픽&gt;.&lt;그룹&gt;.DLT)와 컨슈머 그룹을 단일 출처(yml)에서 관리한다. */
    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Bean
    public ProducerFactory<String, String> notificationProducerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties(null);
        // 프로파일 무관하게 String 고정(이중 인코딩 방지). yml producer.* (acks 등)는 그대로 상속.
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(
            ProducerFactory<String, String> notificationProducerFactory) {
        return new KafkaTemplate<>(notificationProducerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer dlpr = new DeadLetterPublishingRecoverer(
                kafkaTemplate, new NotificationDltDestinationResolver(consumerGroupId));
        NotificationDeadLetterRecoverer recoverer = new NotificationDeadLetterRecoverer(dlpr);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.addNotRetryableExceptions(
                JsonProcessingException.class,
                IllegalArgumentException.class,
                DataIntegrityViolationException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public NewTopic postEventsDltTopic() {
        return TopicBuilder
                .name(NotificationDltDestinationResolver.dltTopicName("post-events", consumerGroupId))
                .partitions(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(RETENTION_14_DAYS_MS))
                .build();
    }

    @Bean
    public NewTopic chatMessageCreatedDltTopic() {
        return TopicBuilder
                .name(NotificationDltDestinationResolver.dltTopicName("chat-message-created", consumerGroupId))
                .partitions(1)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(RETENTION_14_DAYS_MS))
                .build();
    }
}