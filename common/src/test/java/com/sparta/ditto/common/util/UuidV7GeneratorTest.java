package com.sparta.ditto.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UuidV7Generator 테스트")
class UuidV7GeneratorTest {

    private final UuidV7Generator generator = new UuidV7Generator();

    @Test
    @DisplayName("성공 - 버전이 7인 UUID를 생성한다")
    void generate_returns_version7_uuid() {
        // when
        UUID id = generator.generate();

        // then
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    @DisplayName("성공 - 문자열로 파싱 가능한 UUID를 생성한다")
    void generateAsString_returns_parsable_uuid() {
        // when
        String id = generator.generateAsString();

        // then
        assertThat(UUID.fromString(id).version()).isEqualTo(7);
    }

    @Test
    @DisplayName("성공 - 연속 호출 시 값이 단조 증가한다")
    void generate_returns_monotonically_increasing_values() {
        String previous = generator.generateAsString();
        for (int i = 0; i < 100; i++) {
            String current = generator.generateAsString();
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
    }
}
