package com.sparta.ditto.chat.infrastructure.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.sparta.ditto.common.util.UuidV7Generator;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UuidMessageIdGenerator 테스트")
class UuidMessageIdGeneratorTest {

    private final UuidMessageIdGenerator generator =
            new UuidMessageIdGenerator(new UuidV7Generator());

    @Test
    @DisplayName("성공 - 파싱 가능한 UUID 문자열을 생성한다")
    void generate_returns_parsable_uuid() {
        // when
        String id = generator.generate();

        // then
        assertThat(id).isNotBlank();
        assertThatCode(() -> UUID.fromString(id)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("성공 - 호출마다 서로 다른 값을 생성한다")
    void generate_returns_unique_values() {
        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }

    @Test
    @DisplayName("성공 - 버전 7 UUID를 생성한다")
    void generate_returns_version7_uuid() {
        // when
        String id = generator.generate();

        // then
        assertThat(UUID.fromString(id).version()).isEqualTo(7);
    }
}
