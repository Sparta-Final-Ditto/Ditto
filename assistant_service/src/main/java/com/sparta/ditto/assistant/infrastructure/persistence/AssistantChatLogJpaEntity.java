package com.sparta.ditto.assistant.infrastructure.persistence;

import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "assistant_chat_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantChatLogJpaEntity {

    @Id
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

    public static AssistantChatLogJpaEntity from(AssistantChatLog domain, UUID id) {
        AssistantChatLogJpaEntity entity = new AssistantChatLogJpaEntity();
        entity.id = id;
        entity.userId = domain.getUserId();
        entity.question = domain.getQuestion();
        entity.answer = domain.getAnswer();
        entity.matchedDocumentIds = domain.getMatchedDocumentIds();
        entity.similarityScores = domain.getSimilarityScores();
        entity.createdAt = domain.getCreatedAt();
        return entity;
    }

    public AssistantChatLog toDomain() {
        return AssistantChatLog.reconstruct(
                id, userId, question, answer, matchedDocumentIds, similarityScores, createdAt);
    }
}
