package com.sparta.ditto.assistant.evaluation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** LLM-as-judge 채점 결과 */
public record JudgeVerdict(int score, String reason) {

    private static final Pattern VERDICT_PATTERN = Pattern.compile(
            "SCORE:\\s*(?<score>[1-5])\\s*[\\r\\n]+REASON:\\s*(?<reason>.+)", Pattern.DOTALL);

    public static JudgeVerdict parse(String judgeResponse) {
        Matcher matcher = VERDICT_PATTERN.matcher(judgeResponse.trim());
        if (!matcher.find()) {
            return new JudgeVerdict(0, "채점 응답 형식 파싱 실패 — 원문: " + judgeResponse.trim());
        }
        return new JudgeVerdict(
                Integer.parseInt(matcher.group("score")), matcher.group("reason").trim());
    }
}
