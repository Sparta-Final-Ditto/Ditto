package com.sparta.ditto.feed.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.sparta.ditto.feed.application.dto.GetUserPostsQuery;
import com.sparta.ditto.feed.application.dto.UserPostsResult;
import com.sparta.ditto.feed.application.port.OutboxEventPort;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.repository.CommentRepository;
import com.sparta.ditto.feed.domain.repository.OutboxEventRepository;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import com.sparta.ditto.feed.domain.type.MediaType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * getUserPosts 서비스 단위 테스트 (썸네일 조립 + 커서 페이지네이션)
 * - 첫 미디어가 IMAGE → thumbnailUrl=CDN이미지, mediaType=IMAGE, contentSummary=null
 * - 첫 미디어가 VIDEO → thumbnailUrl=CDN비디오, mediaType=VIDEO, contentSummary=null
 * - 텍스트 전용 게시글 (50자 이하) → contentSummary=전체 본문
 * - 텍스트 전용 게시글 (50자 초과) → contentSummary=50자까지 잘림
 * - 21개 초과 → hasNext=true, nextCursor=21번째 게시글 ID
 * - 21개 이하 → hasNext=false, nextCursor=null
 */
@ExtendWith(MockitoExtension.class)
class PostGridServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxEventPort outboxEventPort;

    @InjectMocks
    private PostService postService;

    private static final String CLOUDFRONT_DOMAIN = "https://cdn.example.com";
    private final UUID requesterId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(postService, "cloudfrontDomain", CLOUDFRONT_DOMAIN);
    }

    // -------------------------------------------------------
    // 헬퍼 메서드
    // -------------------------------------------------------

    private Post buildPost(String content) {
        Post post = new Post(targetUserId, "닉네임", content, "서울", 37.5, 127.0,
                LocationScope.PUBLIC, true);
        ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(post, "createdAt", Instant.now());
        return post;
    }

    private void stubRepo(List<Post> posts) {
        when(postRepository.findByUserIdAndScopesWithCursor(
                any(), any(), any(), any(), anyInt()))
                .thenReturn(posts);
    }

    // -------------------------------------------------------
    // 첫 미디어 IMAGE → thumbnailUrl = CDN URL, mediaType = IMAGE
    // -------------------------------------------------------
    @Test
    @DisplayName("첫 미디어가 IMAGE일 때 thumbnailUrl=CDN이미지, mediaType=IMAGE, contentSummary=null")
    void getUserPosts_첫미디어_IMAGE_thumbnailUrl_매핑() {
        // given
        Post post = buildPost("이미지 게시글");
        post.getMediaList().add(new PostMedia(post, "feeds/photo.jpg", MediaType.IMAGE, 1));
        stubRepo(List.of(post));

        // when
        UserPostsResult result = postService.getUserPosts(
                new GetUserPostsQuery(requesterId, targetUserId, null, 21));

        // then
        assertThat(result.posts()).hasSize(1);
        assertThat(result.posts().get(0).thumbnailUrl())
                .isEqualTo(CLOUDFRONT_DOMAIN + "/feeds/photo.jpg");
        assertThat(result.posts().get(0).mediaType()).isEqualTo("IMAGE");
        assertThat(result.posts().get(0).contentSummary()).isNull();
    }

    // -------------------------------------------------------
    // 첫 미디어 VIDEO → thumbnailUrl = 원본 CDN URL, mediaType = VIDEO
    // -------------------------------------------------------
    @Test
    @DisplayName("첫 미디어가 VIDEO일 때 thumbnailUrl=CDN비디오원본URL, mediaType=VIDEO, contentSummary=null")
    void getUserPosts_첫미디어_VIDEO_thumbnailUrl_매핑() {
        // given
        Post post = buildPost("동영상 게시글");
        post.getMediaList().add(new PostMedia(post, "feeds/clip.mp4", MediaType.VIDEO, 1));
        stubRepo(List.of(post));

        // when
        UserPostsResult result = postService.getUserPosts(
                new GetUserPostsQuery(requesterId, targetUserId, null, 21));

        // then
        assertThat(result.posts()).hasSize(1);
        assertThat(result.posts().get(0).thumbnailUrl())
                .isEqualTo(CLOUDFRONT_DOMAIN + "/feeds/clip.mp4");
        assertThat(result.posts().get(0).mediaType()).isEqualTo("VIDEO");
        assertThat(result.posts().get(0).contentSummary()).isNull();
    }

    // -------------------------------------------------------
    // 텍스트 전용 50자 이하 → contentSummary = 전체 본문
    // -------------------------------------------------------
    @Test
    @DisplayName("텍스트 전용 게시글 (50자 이하) → contentSummary에 본문 전체 노출")
    void getUserPosts_텍스트전용_50자이하_contentSummary_전체본문() {
        // given
        String content = "짧은 텍스트 게시글입니다."; // 50자 미만
        Post post = buildPost(content);
        stubRepo(List.of(post));

        // when
        UserPostsResult result = postService.getUserPosts(
                new GetUserPostsQuery(requesterId, targetUserId, null, 21));

        // then
        assertThat(result.posts().get(0).thumbnailUrl()).isNull();
        assertThat(result.posts().get(0).mediaType()).isNull();
        assertThat(result.posts().get(0).contentSummary()).isEqualTo(content);
    }

    // -------------------------------------------------------
    // 텍스트 전용 50자 초과 → contentSummary = 앞 50자만
    // -------------------------------------------------------
    @Test
    @DisplayName("텍스트 전용 게시글 (50자 초과) → contentSummary는 50자까지만 잘려서 반환")
    void getUserPosts_텍스트전용_50자초과_contentSummary_50자잘림() {
        // given
        String content = "가".repeat(60); // 60자
        Post post = buildPost(content);
        stubRepo(List.of(post));

        // when
        UserPostsResult result = postService.getUserPosts(
                new GetUserPostsQuery(requesterId, targetUserId, null, 21));

        // then
        assertThat(result.posts().get(0).contentSummary()).isEqualTo("가".repeat(50));
        assertThat(result.posts().get(0).contentSummary()).hasSize(50);
        assertThat(result.posts().get(0).thumbnailUrl()).isNull();
        assertThat(result.posts().get(0).mediaType()).isNull();
    }

    // -------------------------------------------------------
    // 21개 초과(22개) → hasNext=true, nextCursor=21번째 게시글 ID, 21개 반환
    // -------------------------------------------------------
    @Test
    @DisplayName("리포지토리가 size+1(22개) 반환 시 hasNext=true, 21개 반환, nextCursor=21번째 ID")
    void getUserPosts_22개_hasNext_true_nextCursor_21번째() {
        // given
        // 서비스는 size+1=22개를 리포지토리에 요청한다
        List<Post> posts22 = IntStream.range(0, 22)
                .mapToObj(i -> buildPost("내용 " + i))
                .toList();
        stubRepo(posts22);

        // when
        UserPostsResult result = postService.getUserPosts(
                new GetUserPostsQuery(requesterId, targetUserId, null, 21));

        // then
        assertThat(result.posts()).hasSize(21);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextCursor()).isEqualTo(posts22.get(20).getId());
    }

    // -------------------------------------------------------
    // 21개 이하(10개) → hasNext=false, nextCursor=null, 10개 반환
    // -------------------------------------------------------
    @Test
    @DisplayName("리포지토리가 size 이하(10개) 반환 시 hasNext=false, nextCursor=null")
    void getUserPosts_10개_hasNext_false_nextCursor_null() {
        // given
        List<Post> posts10 = IntStream.range(0, 10)
                .mapToObj(i -> buildPost("내용 " + i))
                .toList();
        stubRepo(posts10);

        // when
        UserPostsResult result = postService.getUserPosts(
                new GetUserPostsQuery(requesterId, targetUserId, null, 21));

        // then
        assertThat(result.posts()).hasSize(10);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.nextCursor()).isNull();
    }
}