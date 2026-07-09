package com.sparta.ditto.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UuidV7IdentifierGenerator 테스트")
class UuidV7IdentifierGeneratorTest {

    private final UuidV7IdentifierGenerator generator = new UuidV7IdentifierGenerator();

    static class SampleEntity {
        @Id
        @Column
        private UUID id;
    }

    @Test
    @DisplayName("성공 - id가 없으면 버전 7 UUID를 새로 생성한다")
    void generate_생성() {
        // given
        SampleEntity entity = new SampleEntity();

        // when
        Object result = generator.generate(null, entity);

        // then
        assertThat(result).isInstanceOf(UUID.class);
        assertThat(((UUID) result).version()).isEqualTo(7);
    }

    @Test
    @DisplayName("성공 - id가 이미 세팅되어 있으면 덮어쓰지 않고 그대로 반환한다")
    void generate_기존id_보존() {
        // given
        UUID existingId = UUID.randomUUID();
        SampleEntity entity = new SampleEntity();
        entity.id = existingId;

        // when
        Object result = generator.generate(null, entity);

        // then
        assertThat(result).isEqualTo(existingId);
    }
}
