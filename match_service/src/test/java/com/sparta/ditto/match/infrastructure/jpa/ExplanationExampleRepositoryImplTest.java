package com.sparta.ditto.match.infrastructure.jpa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExplanationExampleRepositoryImplTest {

    @Test
    @DisplayName("Impl 클래스가 존재한다")
    void implExists() {
        assertThat(ExplanationExampleRepositoryImpl.class).isNotNull();
    }
}