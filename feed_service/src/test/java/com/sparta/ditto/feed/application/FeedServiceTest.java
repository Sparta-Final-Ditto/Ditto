package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.application.dto.result.FeedItemResult;
import com.sparta.ditto.feed.application.dto.result.FeedResult;
import com.sparta.ditto.feed.application.dto.query.GetRandomFeedQuery;
import com.sparta.ditto.feed.application.service.FeedService;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.repository.LikeRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import com.sparta.ditto.feed.domain.type.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private FeedService feedService;

    private final UUID userId = UUID.randomUUID();
    private static final String CLOUDFRONT_DOMAIN = "https://cdn.example.com";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(feedService, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
        lenient().when(likeRepository.findPostIdsByUserIdAndPostIdIn(any(), anyList())).thenReturn(List.of());
    }

    private Post createPost(UUID postId, boolean showLocation, LocationScope scope) {
        Post post = new Post(userId, "테스트유저", "테스트 내용", "서울 성동구",
                37.5563, 127.0374, scope, showLocation);
        ReflectionTestUtils.setField(post, "id", postId);
        ReflectionTestUtils.setField(post, "createdAt", Instant.now());
        return post;
    }

    @Test
    @DisplayName("003-1: cursor=null → 첫 페이지 조회, PUBLIC 게시글 반환")
    void tc003_1_첫페이지_조회() {
        // given
        List<Post> posts = List.of(createPost(UUID.randomUUID(), true, LocationScope.PUBLIC));
        when(postRepository.findFeedByLocationScopeWithCursor(
                eq(List.of(LocationScope.PUBLIC)), eq(null), eq(null), anyInt()))
                .thenReturn(posts);

        // when
        FeedResult result = feedService.getRandomFeed(new GetRandomFeedQuery(userId, null, 20));

        // then
        assertThat(result.feeds()).hasSize(1);
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("003-2: cursor=postId → 해당 게시글의 createdAt·id 기준으로 쿼리")
    void tc003_2_cursor_전달() {
        // given
        UUID cursorPostId = UUID.randomUUID();
        Instant cursorAt = Instant.now().minusSeconds(60);
        Post cursorPost = createPost(cursorPostId, true, LocationScope.PUBLIC);
        ReflectionTestUtils.setField(cursorPost, "createdAt", cursorAt);

        when(postRepository.findById(cursorPostId)).thenReturn(Optional.of(cursorPost));
        when(postRepository.findFeedByLocationScopeWithCursor(
                eq(List.of(LocationScope.PUBLIC)), eq(cursorAt), eq(cursorPostId), anyInt()))
                .thenReturn(List.of());

        // when
        feedService.getRandomFeed(new GetRandomFeedQuery(userId, cursorPostId, 20));

        // then
        verify(postRepository).findFeedByLocationScopeWithCursor(
                eq(List.of(LocationScope.PUBLIC)), eq(cursorAt), eq(cursorPostId), anyInt());
    }

    @Test
    @DisplayName("003-3/4/5: PUBLIC만 반환 — FOLLOWERS_ONLY·PRIVATE 제외")
    void tc003_3_PUBLIC만_조회() {
        // given
        when(postRepository.findFeedByLocationScopeWithCursor(
                eq(List.of(LocationScope.PUBLIC)), any(), any(), anyInt()))
                .thenReturn(List.of());

        // when
        feedService.getRandomFeed(new GetRandomFeedQuery(userId, null, 20));

        // then
        verify(postRepository).findFeedByLocationScopeWithCursor(
                eq(List.of(LocationScope.PUBLIC)), any(), any(), anyInt());
    }

    @Test
    @DisplayName("003-7: showLocation=false → neighborhood=null")
    void tc003_7_showLocation_false_neighborhood_null() {
        // given
        Post post = createPost(UUID.randomUUID(), false, LocationScope.PUBLIC);
        when(postRepository.findFeedByLocationScopeWithCursor(any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));

        // when
        FeedResult result = feedService.getRandomFeed(new GetRandomFeedQuery(userId, null, 20));

        // then
        assertThat(result.feeds().get(0).neighborhood()).isNull();
    }

    @Test
    @DisplayName("003-8: showLocation=true → neighborhood 반환")
    void tc003_8_showLocation_true_neighborhood_반환() {
        // given
        Post post = createPost(UUID.randomUUID(), true, LocationScope.PUBLIC);
        when(postRepository.findFeedByLocationScopeWithCursor(any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));

        // when
        FeedResult result = feedService.getRandomFeed(new GetRandomFeedQuery(userId, null, 20));

        // then
        assertThat(result.feeds().get(0).neighborhood()).isEqualTo("서울 성동구");
    }

    @Test
    @DisplayName("003-10: size+1개 존재 → hasNext=true, nextCursor=마지막 게시글 ID")
    void tc003_10_hasNext_true() {
        // given
        int size = 3;
        List<Post> posts = new ArrayList<>();
        for (int i = 0; i < size + 1; i++) {
            posts.add(createPost(UUID.randomUUID(), true, LocationScope.PUBLIC));
        }
        when(postRepository.findFeedByLocationScopeWithCursor(any(), any(), any(), anyInt()))
                .thenReturn(posts);

        // when
        FeedResult result = feedService.getRandomFeed(new GetRandomFeedQuery(userId, null, size));

        // then
        assertThat(result.hasNext()).isTrue();
        assertThat(result.feeds()).hasSize(size);
        assertThat(result.nextCursor()).isEqualTo(posts.get(size - 1).getId());
    }

    @Test
    @DisplayName("003-11: size 이하 존재 → hasNext=false, nextCursor=null")
    void tc003_11_hasNext_false() {
        // given
        List<Post> posts = List.of(createPost(UUID.randomUUID(), true, LocationScope.PUBLIC));
        when(postRepository.findFeedByLocationScopeWithCursor(any(), any(), any(), anyInt()))
                .thenReturn(posts);

        // when
        FeedResult result = feedService.getRandomFeed(new GetRandomFeedQuery(userId, null, 20));

        // then
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    @DisplayName("003-12: 미디어 포함 → mediaUrl이 CloudFront URL로 반환")
    void tc003_12_미디어_CloudFront_URL() {
        // given
        Post post = createPost(UUID.randomUUID(), true, LocationScope.PUBLIC);
        PostMedia media = new PostMedia(post, "feeds/test.mp4", MediaType.VIDEO, 1);
        ReflectionTestUtils.setField(post, "mediaList", List.of(media));
        when(postRepository.findFeedByLocationScopeWithCursor(any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));

        // when
        FeedResult result = feedService.getRandomFeed(new GetRandomFeedQuery(userId, null, 20));

        // then
        FeedItemResult.MediaResult mediaFile = result.feeds().get(0).mediaFiles().get(0);
        assertThat(mediaFile.s3Key()).isEqualTo("feeds/test.mp4");
        assertThat(mediaFile.mediaUrl()).isEqualTo(CLOUDFRONT_DOMAIN + "/feeds/test.mp4");
        assertThat(mediaFile.mediaType()).isEqualTo("VIDEO");
    }

    @Test
    @DisplayName("003-13: 응답 DTO에 latitude, longitude 필드 없음")
    void tc003_13_latitude_longitude_없음() {
        // when
        var componentNames = java.util.Arrays.stream(FeedItemResult.class.getRecordComponents())
                .map(rc -> rc.getName())
                .toList();

        // then
        assertThat(componentNames).doesNotContain("latitude", "longitude");
    }

    @Test
    @DisplayName("isLiked: 현재 사용자가 좋아요한 게시글 → isLiked=true")
    void isLiked_true() {
        // given
        UUID postId = UUID.randomUUID();
        Post post = createPost(postId, true, LocationScope.PUBLIC);
        when(postRepository.findFeedByLocationScopeWithCursor(any(), any(), any(), anyInt()))
                .thenReturn(List.of(post));
        when(likeRepository.findPostIdsByUserIdAndPostIdIn(eq(userId), anyList()))
                .thenReturn(List.of(postId));

        // when
        FeedResult result = feedService.getRandomFeed(new GetRandomFeedQuery(userId, null, 20));

        // then
        assertThat(result.feeds().get(0).isLiked()).isTrue();
    }
}
