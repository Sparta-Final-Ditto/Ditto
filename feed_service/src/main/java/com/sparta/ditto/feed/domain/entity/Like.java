package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
public class Like extends BaseEntity {

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

    @Column(nullable = false)
    private boolean deletedByPostDeletion = false;

    public Like(UUID postId, UUID userId, String userNickname) {
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
    }

    /** 테스트 전용 — 프로덕션 코드에서 사용 금지 */
    public Like(UUID postId, UUID userId) {
        this(postId, userId, "user");
    }
}
