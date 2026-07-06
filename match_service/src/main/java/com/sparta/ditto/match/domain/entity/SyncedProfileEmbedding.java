package com.sparta.ditto.match.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * embedding_service의 user_profile_embeddings를 복제한 Read Model (CQRS 패턴)
 * match_service에서 HNSW 인덱스를 활용한 직접 벡터 검색용
 */
@Entity
@Table(name = "synced_profile_embeddings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncedProfileEmbedding {

    @Id
    private UUID userId;

    @Column(nullable = false, columnDefinition = "vector(768)")
    private String vector;  // pgvector는 String으로 매핑

    @Column(length = 6)
    private String gender;

    private LocalDate birthdate;

    @Column(nullable = false)
    private Boolean active;

    @Column(nullable = false)
    private Instant syncedAt;

    public static SyncedProfileEmbedding of(
            UUID userId,
            float[] vector,
            String gender,
            LocalDate birthdate,
            boolean active
    ) {
        SyncedProfileEmbedding entity = new SyncedProfileEmbedding();
        entity.userId = userId;
        entity.vector = floatArrayToVectorString(vector);
        entity.gender = gender;
        entity.birthdate = birthdate;
        entity.active = active;
        entity.syncedAt = Instant.now();
        return entity;
    }

    public void updateVector(float[] newVector, String gender, LocalDate birthdate, boolean active) {
        this.vector = floatArrayToVectorString(newVector);
        this.gender = gender;
        this.birthdate = birthdate;
        this.active = active;
        this.syncedAt = Instant.now();
    }

    // float[] → "[0.1,0.2,0.3]" pgvector 형식 변환
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