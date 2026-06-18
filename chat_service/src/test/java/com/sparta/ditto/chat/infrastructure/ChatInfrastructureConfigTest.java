package com.sparta.ditto.chat.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.chat.infrastructure.kafka.KafkaConfig;
import com.sparta.ditto.chat.infrastructure.mongo.MongoConfig;
import com.sparta.ditto.chat.infrastructure.redis.RedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Chat Infrastructure 설정 테스트")
class ChatInfrastructureConfigTest {

    @Test
    @DisplayName("성공 - 인프라 설정 클래스를 생성한다")
    void create_config_classes_success() {
        // when & then
        assertThat(new KafkaConfig()).isNotNull();
        assertThat(new MongoConfig()).isNotNull();
        assertThat(new RedisConfig()).isNotNull();
    }
}
