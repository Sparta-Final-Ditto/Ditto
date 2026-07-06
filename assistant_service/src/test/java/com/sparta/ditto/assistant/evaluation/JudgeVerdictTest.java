package com.sparta.ditto.assistant.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JudgeVerdictTest {

    @Test
    @DisplayName("parse()는 SCORE/REASON 형식의 judge 응답을 파싱한다")
    void parse_parsesWellFormedResponse() {
        JudgeVerdict verdict = JudgeVerdict.parse("SCORE: 5\nREASON: 참고 문서 내용과 일치합니다.");

        assertThat(verdict.score()).isEqualTo(5);
        assertThat(verdict.reason()).isEqualTo("참고 문서 내용과 일치합니다.");
    }

    @Test
    @DisplayName("parse()는 형식이 어긋난 응답을 score 0으로 안전하게 처리한다")
    void parse_fallsBackToZero_whenResponseIsMalformed() {
        // 점수만 있고 이유가 없는 부실한 judge 응답 예시
        // SCORE:/REASON: 형식이 아니므로 파싱 실패로 폴백
        JudgeVerdict verdict = JudgeVerdict.parse("음, 대략 4점 정도 될 것 같아요.");

        assertThat(verdict.score()).isZero();
        assertThat(verdict.reason()).contains("파싱 실패");
    }
}
