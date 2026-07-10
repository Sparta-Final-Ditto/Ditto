package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.service.PostAccessValidator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 단건 게시글 접근제어(visibility)를 <b>트랜잭션 밖</b>에서 검증하는 공용 가드.
 *
 * <p>{@link PostCreateFacade}/{@link PostInteractionFacade} 선례를 따라, 외부 Feign I/O(팔로우 확인)는
 * DB 트랜잭션 밖에서 수행하고 실제 조회/변경은 {@code @Transactional} 서비스 메서드에 위임한다.
 * FOLLOWERS_ONLY 게시글이고 작성자 본인이 아닐 때만 팔로우 목록을 조회하므로,
 * PUBLIC/PRIVATE 접근에는 외부 호출이 발생하지 않는다.</p>
 *
 * <p>게시글이 없거나(soft delete 포함) 접근 권한이 없으면 존재를 숨기기 위해
 * {@link PostNotFoundException}(404)을 던진다.</p>
 */
@Component
@RequiredArgsConstructor
public class PostAccessGuard {

    private final PostRepository postRepository;
    private final FollowServicePort followServicePort;

    /**
     * 게시글 접근 가능 여부를 검증하고 작성자 ID를 반환한다.
     * 외부 호출(팔로우 확인)은 이 메서드 내부(트랜잭션 밖)에서만 발생한다.
     */
    public UUID requireAccessiblePostOwner(UUID postId, UUID requesterId) {
        Post post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(PostNotFoundException::new);
        PostAccessValidator.validate(
                post.getVisibility(), post.getUserId(), requesterId,
                () -> isFollowing(requesterId, post.getUserId()));
        return post.getUserId();
    }

    private boolean isFollowing(UUID requesterId, UUID ownerId) {
        return followServicePort.getFollowingIds(requesterId)
                .followingUserIds().contains(ownerId);
    }
}
