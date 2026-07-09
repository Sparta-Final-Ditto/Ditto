package com.sparta.ditto.assistant.infrastructure.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class AssistantDocumentChunkerTest {

    private final AssistantDocumentChunker chunker = new AssistantDocumentChunker();

    @Test
    @DisplayName("chunk()는 검색 대상 content로 답변만 사용하고, 질문은 메타데이터에 보존한다")
    void chunk_buildsDocumentWithAnswerOnlyContentAndMetadata() {
        FaqItem item = new FaqItem("faq-001", "회원가입 방법", "회원가입은 어떻게 하나요?", "이메일로 가입합니다.");

        Document document = chunker.chunk(item, "FAQ");

        assertThat(document.getText()).isEqualTo("이메일로 가입합니다.");
        assertThat(document.getMetadata())
                .containsEntry("sourceType", "FAQ")
                .containsEntry("title", "회원가입 방법")
                .containsEntry("question", "회원가입은 어떻게 하나요?")
                .containsEntry("version", 1);
    }

    @Test
    @DisplayName("chunk()는 같은 id에 대해 항상 같은 UUID를 생성한다")
    void chunk_generatesDeterministicIdFromFaqId() {
        FaqItem item = new FaqItem("faq-001", "제목", "질문", "답변");
        String expectedId =
                UUID.nameUUIDFromBytes("faq-001".getBytes(StandardCharsets.UTF_8)).toString();

        Document first = chunker.chunk(item, "FAQ");
        Document second = chunker.chunk(item, "FAQ");

        assertThat(first.getId()).isEqualTo(expectedId);
        assertThat(second.getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("chunk()는 전달된 sourceType을 메타데이터에 그대로 반영한다")
    void chunk_usesGivenSourceType() {
        FaqItem item = new FaqItem("policy-user-001", "회원가입 정책", "질문", "답변");

        Document document = chunker.chunk(item, "POLICY");

        assertThat(document.getMetadata()).containsEntry("sourceType", "POLICY");
    }
}
