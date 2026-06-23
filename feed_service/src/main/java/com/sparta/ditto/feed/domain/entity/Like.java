package com.sparta.ditto.feed.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "likes",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_likes_post_id_user_id",
                        columnNames = {"post_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_likes_post_id_created_at",
                        columnList = "post_id, created_at DESC")
        }
)
public class Like {

    private static final String TEMP_USER_NICKNAME = "user";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID postId;

    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_nickname", nullable = false, updatable = false, length = 100)
    private String userNickname;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Like(UUID postId, UUID userId) {
        this.postId = postId;
        this.userId = userId;
        this.userNickname = TEMP_USER_NICKNAME;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
