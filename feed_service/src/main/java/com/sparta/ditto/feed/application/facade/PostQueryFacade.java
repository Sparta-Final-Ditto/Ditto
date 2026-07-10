package com.sparta.ditto.feed.application.facade;

import com.sparta.ditto.feed.application.dto.query.GetCommentsQuery;
import com.sparta.ditto.feed.application.dto.query.GetLikesQuery;
import com.sparta.ditto.feed.application.dto.result.CommentListResult;
import com.sparta.ditto.feed.application.dto.result.LikeListResult;
import com.sparta.ditto.feed.application.dto.result.PostDetailResult;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.application.service.PostService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 단건 게시글 읽기(상세·댓글·좋아요) 접근제어를 조율하는 퍼사드(트랜잭션 없음).
 *
 * <p>{@link PostAccessGuard}로 visibility 접근제어(외부 팔로우 확인은 트랜잭션 밖)를 먼저 수행한 뒤,
 * {@code @Transactional} 조회 서비스에 위임한다. 진입 메서드와 트랜잭션 메서드를 서로 다른 빈에 두어
 * self-invocation 무력화를 방지한다.</p>
 */
@Component
@RequiredArgsConstructor
public class PostQueryFacade {

    private final PostAccessGuard postAccessGuard;
    private final PostService postService;
    private final PostInteractionService postInteractionService;

    public PostDetailResult getPostDetail(UUID postId, UUID requesterId) {
        postAccessGuard.requireAccessiblePostOwner(postId, requesterId);
        return postService.getPostDetail(postId, requesterId);
    }

    public CommentListResult getComments(GetCommentsQuery query) {
        postAccessGuard.requireAccessiblePostOwner(query.postId(), query.requesterId());
        return postInteractionService.getComments(query);
    }

    public LikeListResult getLikes(GetLikesQuery query) {
        postAccessGuard.requireAccessiblePostOwner(query.postId(), query.requesterId());
        return postInteractionService.getLikes(query);
    }
}
