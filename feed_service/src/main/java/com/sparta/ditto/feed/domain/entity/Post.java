package com.sparta.ditto.feed.domain.entity;

import com.sparta.ditto.common.entity.BaseEntity;
import com.sparta.ditto.feed.domain.exception.DuplicateSortOrderException;
import com.sparta.ditto.feed.domain.exception.ImageCountExceededException;
import com.sparta.ditto.feed.domain.exception.VideoCountExceededException;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.domain.type.MediaType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "posts",
        indexes = {
                @Index(name = "idx_posts_created_at_id",
                        columnList = "created_at DESC, id DESC"),
                @Index(name = "idx_posts_user_id_created_at",
                        columnList = "user_id, created_at DESC"),
                @Index(name = "idx_posts_visibility_created_at_id",
                        columnList = "visibility, created_at DESC, id DESC")
        }
)
/** 게시글 엔티티 */
public class Post extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID userId;

    // 게시글 생성 시점의 닉네임을 비정규화 저장 (User Service 닉네임 변경과 무관하게 유지)
    @Column(length = 50)
    private String authorNickname;

    @Column(length = 500)
    private String content;

    @Column
    private String neighborhood;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility = Visibility.PUBLIC;

    @Column(nullable = false)
    private Boolean showLocation = true;

    @Column(nullable = false)
    private Integer likeCount = 0;

    @Column(nullable = false)
    private Integer commentCount = 0;

    @OneToMany(
            mappedBy = "post",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @OrderBy("sortOrder ASC")
    private List<PostMedia> mediaList = new ArrayList<>();

    @OneToMany(
            mappedBy = "post",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    private List<PostTag> tags = new ArrayList<>();

    public Post(UUID userId, String authorNickname, String content, String neighborhood,
            Double latitude, Double longitude,
            Visibility visibility, Boolean showLocation) {
        this.userId = userId;
        this.authorNickname = authorNickname;
        this.content = content;
        this.neighborhood = neighborhood;
        this.latitude = latitude;
        this.longitude = longitude;
        this.visibility = visibility != null ? visibility : Visibility.PUBLIC;
        this.showLocation = showLocation != null ? showLocation : true;
    }

    public void addMedia(PostMedia media) {
        boolean hasDuplicateSortOrder = this.mediaList.stream()
                .anyMatch(m -> m.getSortOrder().equals(media.getSortOrder()));
        if (hasDuplicateSortOrder) {
            throw new DuplicateSortOrderException();
        }
        long imageCount = this.mediaList.stream()
                .filter(m -> m.getMediaType() == MediaType.IMAGE).count();
        long videoCount = this.mediaList.stream()
                .filter(m -> m.getMediaType() == MediaType.VIDEO).count();
        if (media.getMediaType() == MediaType.IMAGE && imageCount >= 5) {
            throw new ImageCountExceededException();
        }
        if (media.getMediaType() == MediaType.VIDEO && videoCount >= 1) {
            throw new VideoCountExceededException();
        }
        this.mediaList.add(media);
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void incrementCommentCount() {
        this.commentCount++;
    }

    public void decrementCommentCount() {
        if (this.commentCount > 0) {
            this.commentCount--;
        }
    }
}
