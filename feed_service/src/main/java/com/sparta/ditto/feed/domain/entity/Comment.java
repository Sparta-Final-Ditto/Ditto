package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "comments",
        indexes = {
                @Index(name = "idx_comments_post_id_created_at_id",
                        columnList = "post_id, created_at ASC, id ASC")
        }
)
public class Comment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID postId;

    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "user_nickname", length = 50, nullable = false, updatable = false)
    private String userNickname;

    @Column(length = 200, nullable = false)
    private String content;

    public Comment(UUID postId, UUID userId, String userNickname, String content) {
        this.postId = postId;
        this.userId = userId;
        this.userNickname = userNickname;
        this.content = content;
    }

    public Comment(UUID postId, UUID userId, String content) {
        this(postId, userId, "", content);
    }
}
