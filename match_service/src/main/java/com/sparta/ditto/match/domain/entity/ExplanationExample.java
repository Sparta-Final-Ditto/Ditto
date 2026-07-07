package com.sparta.ditto.match.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 매칭 설명 RAG용 예시 (시드 골든셋 + 실제 LLM 생성 이력)
 * embedding_service에서 계산해온 벡터를 그대로 저장 (match_service는 임베딩을 직접 계산하지 않음)
 */
@Entity
@Table(name = "explanation_examples")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExplanationExample {

    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "vector(768)")
    private String vector;  // pgvector는 String으로 매핑 ("[0.1,0.2,...]")

    @Column(name = "common_tags", length = 200)
    private String commonTags;

    private Integer score;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;  // SEED | GENERATED

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ExplanationExample of(
            UUID id,
            String content,
            float[] vector,
            String commonTags,
            Integer score,
            String sourceType
    ) {
        ExplanationExample example = new ExplanationExample();
        example.id = id;
        example.content = content;
        example.vector = floatArrayToVectorString(vector);
        example.commonTags = commonTags;
        example.score = score;
        example.sourceType = sourceType;
        example.createdAt = Instant.now();
        return example;
    }

    private static String floatArrayToVectorString(float[] arr) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(arr[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
