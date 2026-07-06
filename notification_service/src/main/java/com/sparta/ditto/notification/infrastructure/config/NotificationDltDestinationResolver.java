package com.sparta.ditto.notification.infrastructure.config;

import java.util.function.BiFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;

/**
 * 실패 레코드를 "&lt;원본토픽&gt;.&lt;컨슈머그룹&gt;.DLT"로 라우팅한다.
 *
 * <p>파티션은 지정하지 않는다(-1). 원본 토픽의 파티션 수와 DLT(1 파티션)가 달라도 안전하도록
 * KafkaProducer가 파티션을 결정하게 한다(DeadLetterPublishingRecoverer는 음수 파티션을 미지정으로 처리).
 * post-events는 match-service 등 다른 컨슈머 그룹도 구독하므로, Spring 기본 "&lt;토픽&gt;.DLT"를 쓰지 않고
 * 그룹명을 포함해 서비스 간 실패 메시지가 섞이지 않게 한다(TRD 10장).
 */
public class NotificationDltDestinationResolver
        implements BiFunction<ConsumerRecord<?, ?>, Exception, TopicPartition> {

    /** 음수 파티션 = 미지정(KafkaProducer가 결정). */
    static final int UNSPECIFIED_PARTITION = -1;

    private final String consumerGroupId;

    public NotificationDltDestinationResolver(String consumerGroupId) {
        this.consumerGroupId = consumerGroupId;
    }

    public static String dltTopicName(String originalTopic, String consumerGroupId) {
        return originalTopic + "." + consumerGroupId + ".DLT";
    }

    @Override
    public TopicPartition apply(ConsumerRecord<?, ?> record, Exception exception) {
        return new TopicPartition(
                dltTopicName(record.topic(), consumerGroupId), UNSPECIFIED_PARTITION);
    }
}