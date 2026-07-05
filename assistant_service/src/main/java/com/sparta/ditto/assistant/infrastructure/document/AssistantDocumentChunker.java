package com.sparta.ditto.assistant.infrastructure.document;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

/** FAQ/정책 문서를 Q&A 쌍 단위로 청킹 — 질문/답변 한 쌍이 청크 1개 */
@Component
public class AssistantDocumentChunker {

    private static final int DOCUMENT_VERSION = 1;

    public Document chunk(FaqItem item, String sourceType) {
        String content = "Q: %s\nA: %s".formatted(item.question(), item.answer());
        Map<String, Object> metadata = Map.of(
                "sourceType", sourceType,
                "title", item.title(),
                "version", DOCUMENT_VERSION
        );
        // FAQ 원본 id로부터 UUID를 만들어 재적재 시 upsert
        String documentId = UUID.nameUUIDFromBytes(item.id().getBytes(StandardCharsets.UTF_8))
                .toString();
        return new Document(documentId, content, metadata);
    }
}
