package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.feed.domain.exception.InvalidPostMediaTypeException;
import com.sparta.ditto.feed.domain.type.MediaType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "post_media",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_post_media_post_id_sort_order",
                        columnNames = {"post_id", "sort_order"}
                )
        },
        indexes = {
                @Index(name = "idx_post_media_post_id_sort_order",
                        columnList = "post_id, sort_order")
        }
)
/** 게시글 미디어(이미지·영상) 엔티티 */
public class PostMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    private Post post;

    @Column(nullable = false)
    private String s3Key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;

    @Column(nullable = false)
    private Integer sortOrder;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public PostMedia(Post post, String s3Key, MediaType mediaType, Integer sortOrder) {
        this.post = post;
        this.s3Key = s3Key;
        this.mediaType = mediaType;
        this.sortOrder = sortOrder;
    }

    public PostMedia(Post post, String s3Key, String rawMediaType, Integer sortOrder) {
        this(post, s3Key, parseMediaType(rawMediaType), sortOrder);
    }

    private static MediaType parseMediaType(String rawMediaType) {
        try {
            return MediaType.valueOf(rawMediaType);
        } catch (IllegalArgumentException e) {
            throw new InvalidPostMediaTypeException();
        }
    }
}
