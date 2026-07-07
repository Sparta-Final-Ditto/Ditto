package com.sparta.ditto.assistant.presentation.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantChatResponseTest {

    @Test
    @DisplayName("from()은 AssistantAnswerResult를 AssistantChatResponse로 변환한다")
    void from_mapsResultToResponse() {
        AssistantAnswerResult result = new AssistantAnswerResult(
                "답변입니다",
                List.of(new AssistantAnswerResult.SourceResult("회원가입 방법", "FAQ")));

        AssistantChatResponse response = AssistantChatResponse.from(result);

        assertThat(response.answer()).isEqualTo("답변입니다");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).title()).isEqualTo("회원가입 방법");
        assertThat(response.sources().get(0).sourceType()).isEqualTo("FAQ");
    }

    @Test
    @DisplayName("from()은 출처가 없으면 빈 sources 리스트를 반환한다")
    void from_withNoSources_returnsEmptyList() {
        AssistantAnswerResult result = new AssistantAnswerResult("확인이 어렵습니다.", List.of());

        AssistantChatResponse response = AssistantChatResponse.from(result);

        assertThat(response.sources()).isEmpty();
    }
}
