package com.sparta.ditto.assistant.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssistantChatLogTest {

    @Test
    @DisplayName("of()로 생성 시 모든 필드가 올바르게 설정된다")
    void of_setsAllFieldsCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();

        AssistantChatLog chatLog = AssistantChatLog.of(
                userId, "질문입니다", "답변입니다", List.of(documentId), List.of(0.9f));

        assertThat(chatLog.getUserId()).isEqualTo(userId);
        assertThat(chatLog.getQuestion()).isEqualTo("질문입니다");
        assertThat(chatLog.getAnswer()).isEqualTo("답변입니다");
        assertThat(chatLog.getMatchedDocumentIds()).containsExactly(documentId);
        assertThat(chatLog.getSimilarityScores()).containsExactly(0.9f);
        assertThat(chatLog.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("of()는 userId가 없어도(비로그인) 생성할 수 있다")
    void of_allowsNullUserId() {
        AssistantChatLog chatLog = AssistantChatLog.of(null, "질문", "답변", List.of(), List.of());

        assertThat(chatLog.getUserId()).isNull();
        assertThat(chatLog.getMatchedDocumentIds()).isEmpty();
    }
}
