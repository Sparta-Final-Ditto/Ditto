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
    @DisplayName("chunk()는 Q&A를 하나의 텍스트로 합치고 메타데이터를 채운다")
    void chunk_buildsDocumentWithQaContentAndMetadata() {
        FaqItem item = new FaqItem("faq-001", "회원가입 방법", "회원가입은 어떻게 하나요?", "이메일로 가입합니다.");

        Document document = chunker.chunk(item);

        assertThat(document.getText()).isEqualTo("Q: 회원가입은 어떻게 하나요?\nA: 이메일로 가입합니다.");
        assertThat(document.getMetadata())
                .containsEntry("sourceType", "FAQ")
                .containsEntry("title", "회원가입 방법")
                .containsEntry("version", 1);
    }

    @Test
    @DisplayName("chunk()는 같은 id에 대해 항상 같은 UUID를 생성한다")
    void chunk_generatesDeterministicIdFromFaqId() {
        FaqItem item = new FaqItem("faq-001", "제목", "질문", "답변");
        String expectedId =
                UUID.nameUUIDFromBytes("faq-001".getBytes(StandardCharsets.UTF_8)).toString();

        Document first = chunker.chunk(item);
        Document second = chunker.chunk(item);

        assertThat(first.getId()).isEqualTo(expectedId);
        assertThat(second.getId()).isEqualTo(first.getId());
    }
}
