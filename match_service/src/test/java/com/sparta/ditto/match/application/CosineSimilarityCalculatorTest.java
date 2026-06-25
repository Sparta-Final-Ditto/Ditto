package com.sparta.ditto.match.application;

import com.sparta.ditto.match.application.service.CosineSimilarityCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CosineSimilarityCalculatorTest {

    private final CosineSimilarityCalculator calculator = new CosineSimilarityCalculator();

    @Test
    @DisplayName("동일한 벡터의 유사도는 1.0이다")
    void calculate_sameVector_returnsOne() {
        float[] v = {1f, 0f, 0f};
        assertThat(calculator.calculate(v, v)).isCloseTo(1.0f, within(0.001f));
    }

    @Test
    @DisplayName("반대 방향 벡터의 유사도는 0.0이다 (정규화 후)")
    void calculate_oppositeVector_returnsZero() {
        float[] a = {1f, 0f, 0f};
        float[] b = {-1f, 0f, 0f};
        assertThat(calculator.calculate(a, b)).isCloseTo(0.0f, within(0.001f));
    }

    @Test
    @DisplayName("직교 벡터의 유사도는 0.5이다 (정규화 후)")
    void calculate_orthogonalVector_returnsHalf() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertThat(calculator.calculate(a, b)).isCloseTo(0.5f, within(0.001f));
    }

    @Test
    @DisplayName("null 벡터는 0.0을 반환한다")
    void calculate_nullVector_returnsZero() {
        assertThat(calculator.calculate(null, new float[]{1f})).isEqualTo(0f);
        assertThat(calculator.calculate(new float[]{1f}, null)).isEqualTo(0f);
    }

    @Test
    @DisplayName("길이가 다른 벡터는 0.0을 반환한다")
    void calculate_differentLength_returnsZero() {
        float[] a = {1f, 2f};
        float[] b = {1f, 2f, 3f};
        assertThat(calculator.calculate(a, b)).isEqualTo(0f);
    }

    @Test
    @DisplayName("영벡터는 0.0을 반환한다")
    void calculate_zeroVector_returnsZero() {
        float[] a = {0f, 0f, 0f};
        float[] b = {1f, 2f, 3f};
        assertThat(calculator.calculate(a, b)).isEqualTo(0f);
    }
}