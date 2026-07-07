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
        Map<String, Object> metadata = Map.of(
                "sourceType", sourceType,
                "title", item.title(),
                "itemId", item.id(),
                "question", item.question(),
                "version", DOCUMENT_VERSION
        );
        return new Document(toDocumentId(item.id()), item.answer(), metadata);
    }

    // FAQ/정책 원본 id로부터 결정적 UUID를 생성 — 재적재 시 upsert
    public static String toDocumentId(String itemId) {
        return UUID.nameUUIDFromBytes(itemId.getBytes(StandardCharsets.UTF_8)).toString();
    }
}
