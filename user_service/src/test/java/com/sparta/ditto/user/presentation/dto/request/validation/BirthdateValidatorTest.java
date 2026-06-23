package com.sparta.ditto.user.presentation.dto.request.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BirthdateValidatorTest {

    private BirthdateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BirthdateValidator();
    }

    @Nested
    class IsValid {

        @Test
        void 통과_1900년_1월_1일() {
            assertThat(validator.isValid(LocalDate.of(1900, 1, 1), null)).isTrue();
        }

        @Test
        void 통과_1900년_이후() {
            assertThat(validator.isValid(LocalDate.of(1990, 5, 15), null)).isTrue();
        }

        @Test
        void 차단_1899년_12월_31일() {
            assertThat(validator.isValid(LocalDate.of(1899, 12, 31), null)).isFalse();
        }

        @Test
        void 차단_1800년대() {
            assertThat(validator.isValid(LocalDate.of(1850, 6, 1), null)).isFalse();
        }

        @Test
        void null_통과_NotNull이_처리() {
            assertThat(validator.isValid(null, null)).isTrue();
        }
    }
}