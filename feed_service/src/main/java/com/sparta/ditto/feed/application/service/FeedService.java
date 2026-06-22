package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.dto.FeedItemResult;
import com.sparta.ditto.feed.application.dto.FeedResult;
import com.sparta.ditto.feed.application.dto.GetRandomFeedQuery;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;

    @Value("${app.cloudfront.domain}")
    private String cloudfrontDomain;

    @Transactional(readOnly = true)
    public FeedResult getRandomFeed(GetRandomFeedQuery query) {
        Instant cursorAt = null;
        UUID cursorId = null;
        if (query.cursorPostId() != null) {
            Post cursorPost = postRepository.findById(query.cursorPostId()).orElse(null);
            if (cursorPost != null) {
                cursorAt = cursorPost.getCreatedAt();
                cursorId = cursorPost.getId();
            }
        }

        List<Post> posts = postRepository.findFeedByLocationScopeWithCursor(
                List.of(LocationScope.PUBLIC), cursorAt, cursorId, query.size() + 1
        );

        boolean hasNext = posts.size() > query.size();
        List<Post> feedPosts = hasNext ? posts.subList(0, query.size()) : posts;

        List<UUID> postIds = feedPosts.stream().map(Post::getId).toList();
        Set<UUID> likedPostIds = postIds.isEmpty() ? Set.of()
                : new HashSet<>(likeRepository.findPostIdsByUserIdAndPostIdIn(query.userId(), postIds));

        List<FeedItemResult> feeds = feedPosts.stream()
                .map(p -> FeedItemResult.from(p, likedPostIds.contains(p.getId()), cloudfrontDomain))
                .toList();

        UUID nextCursor = hasNext ? feedPosts.get(feedPosts.size() - 1).getId() : null;
        return new FeedResult(feeds, nextCursor, hasNext);
    }
}
