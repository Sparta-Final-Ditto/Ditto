package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.presentation.dto.response.FeedItemResponse;
import com.sparta.ditto.feed.presentation.dto.response.RandomFeedResponse;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 피드 조회 서비스 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;

    @Value("${app.cloudfront.domain}")
    private String cloudfrontDomain;

    @Transactional(readOnly = true)
    public RandomFeedResponse getRandomFeed(UUID userId, UUID cursorPostId, int size) {
        // cursor 해석: postId → (createdAt, id)
        Instant cursorAt = null;
        UUID cursorId = null;
        if (cursorPostId != null) {
            Post cursorPost = postRepository.findById(cursorPostId).orElse(null);
            if (cursorPost != null) {
                cursorAt = cursorPost.getCreatedAt();
                cursorId = cursorPost.getId();
            }
        }

        // size+1개 조회
        List<Post> posts = postRepository.findFeedByLocationScopeWithCursor(
                List.of(LocationScope.PUBLIC), cursorAt, cursorId,
                PageRequest.of(0, size + 1)
        );

        // hasNext 판단
        boolean hasNext = posts.size() > size;
        List<Post> feedPosts = hasNext ? posts.subList(0, size) : posts;

        // isLiked 배치 조회 (N+1 방지)
        List<UUID> postIds = feedPosts.stream().map(Post::getId).toList();
        Set<UUID> likedPostIds = postIds.isEmpty() ? Set.of()
                : new HashSet<>(likeRepository.findPostIdsByUserIdAndPostIdIn(userId, postIds));

        // 응답 조립
        List<FeedItemResponse> feeds = feedPosts.stream()
                .map(p -> FeedItemResponse.from(p, likedPostIds.contains(p.getId()), cloudfrontDomain))
                .toList();

        UUID nextCursor = hasNext ? feedPosts.get(feedPosts.size() - 1).getId() : null;
        return new RandomFeedResponse(feeds, nextCursor, hasNext);
    }
}