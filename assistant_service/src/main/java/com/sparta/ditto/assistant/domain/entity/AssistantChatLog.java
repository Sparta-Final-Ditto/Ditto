package com.sparta.ditto.assistant.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 질문/답변/근거 문서/유사도 로그. 정확도 평가(Hit@K, Faithfulness)용 */
@Entity
@Table(name = "assistant_chat_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantChatLog {

    // TODO: 공통모듈에 UUID v7 generator 적용 후 교체 (chat_service의 UuidMessageIdGenerator와 동일한 이유)
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String question;

    @Column(nullable = false)
    private String answer;

    @Column(name = "matched_document_ids", nullable = false, columnDefinition = "uuid[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<UUID> matchedDocumentIds;

    @Column(name = "similarity_scores", columnDefinition = "float4[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private List<Float> similarityScores;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AssistantChatLog of(
            UUID userId,
            String question,
            String answer,
            List<UUID> matchedDocumentIds,
            List<Float> similarityScores
    ) {
        AssistantChatLog chatLog = new AssistantChatLog();
        chatLog.userId = userId;
        chatLog.question = question;
        chatLog.answer = answer;
        chatLog.matchedDocumentIds = matchedDocumentIds;
        chatLog.similarityScores = similarityScores;
        chatLog.createdAt = Instant.now();
        return chatLog;
    }
}
