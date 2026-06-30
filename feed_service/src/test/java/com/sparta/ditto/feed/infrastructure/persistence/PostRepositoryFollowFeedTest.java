package com.sparta.ditto.feed.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 팔로우 피드 조회 쿼리 슬라이스 테스트.
 * [PUBLIC, FOLLOWERS_ONLY] 스코프 필터와 cursor 기반 페이징을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import(PostRepositoryImpl.class)
class PostRepositoryFollowFeedTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TestEntityManager em;

    private static final Instant BASE = Instant.parse("2026-06-16T00:00:00Z");
    private static final List<Visibility> FOLLOW_SCOPES =
            List.of(Visibility.PUBLIC, Visibility.FOLLOWERS_ONLY);

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private Post savePost(UUID authorId, Visibility scope, Instant createdAt) {
        Post post = new Post(authorId, "팔로잉유저", "내용", "서울 마포구",
                37.5563, 127.0374, scope, true);
        ReflectionTestUtils.setField(post, "createdAt", createdAt);
        ReflectionTestUtils.setField(post, "updatedAt", createdAt);
        return em.persistAndFlush(post);
    }

    /** 팔로잉 1명 + 해당 스코프 게시글 1개를 저장하고 반환한다. */
    private record FollowerPost(UUID followingId, Post post) {}

    private FollowerPost singleFollowerWithPost(Visibility scope) {
        UUID followingId = UUID.randomUUID();
        Post post = savePost(followingId, scope, BASE.plusSeconds(10));
        return new FollowerPost(followingId, post);
    }

    /** FOLLOW_SCOPES 고정, cursor 없음으로 팔로우 피드를 조회한다. */
    private List<Post> findWithFollowScopes(List<UUID> userIds, int limit) {
        return postRepository.findFeedByUserIdsAndVisibilityWithCursor(
                userIds, FOLLOW_SCOPES, null, null, limit);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("004-5: 팔로잉 유저의 PUBLIC 게시글이 결과에 포함된다")
    void findFollowFeed_includesPublicPost() {
        FollowerPost fp = singleFollowerWithPost(Visibility.PUBLIC);

        List<Post> result = findWithFollowScopes(List.of(fp.followingId()), 10);

        assertThat(result).extracting(Post::getId).contains(fp.post().getId());
    }

    @Test
    @DisplayName("004-6: 팔로잉 유저의 FOLLOWERS_ONLY 게시글이 결과에 포함된다")
    void findFollowFeed_includesFollowersOnlyPost() {
        FollowerPost fp = singleFollowerWithPost(Visibility.FOLLOWERS_ONLY);

        List<Post> result = findWithFollowScopes(List.of(fp.followingId()), 10);

        assertThat(result).extracting(Post::getId).contains(fp.post().getId());
    }

    @Test
    @DisplayName("004-7: 팔로잉 유저의 PRIVATE 게시글은 결과에서 제외된다")
    void findFollowFeed_excludesPrivatePost() {
        FollowerPost fp = singleFollowerWithPost(Visibility.PRIVATE);

        List<Post> result = findWithFollowScopes(List.of(fp.followingId()), 10);

        assertThat(result).extracting(Post::getId).doesNotContain(fp.post().getId());
    }

    @Test
    @DisplayName("004-10: cursor/size 페이징 — size+1 조회 후 커서 기준 다음 페이지가 정확히 반환된다")
    void findFollowFeed_paginatesByCursor() {
        UUID author = UUID.randomUUID();
        Post t1 = savePost(author, Visibility.PUBLIC, BASE.plus(1, ChronoUnit.HOURS));
        Post t2 = savePost(author, Visibility.PUBLIC, BASE.plus(2, ChronoUnit.HOURS));
        Post t3 = savePost(author, Visibility.PUBLIC, BASE.plus(3, ChronoUnit.HOURS));
        Post t4 = savePost(author, Visibility.PUBLIC, BASE.plus(4, ChronoUnit.HOURS));

        // 첫 페이지: size=2 → size+1=3 조회, 최신순 desc → t4, t3, t2
        List<Post> page1 = findWithFollowScopes(List.of(author), 3);

        assertThat(page1).hasSize(3);
        assertThat(page1).extracting(Post::getId)
                .containsExactly(t4.getId(), t3.getId(), t2.getId());

        // 두 번째 페이지: t3(page1[1])을 커서로 사용 → t2, t1
        Post cursorPost = page1.get(1);
        List<Post> page2 = postRepository.findFeedByUserIdsAndVisibilityWithCursor(
                List.of(author), FOLLOW_SCOPES,
                cursorPost.getCreatedAt(), cursorPost.getId(), 3);

        assertThat(page2).extracting(Post::getId)
                .containsExactly(t2.getId(), t1.getId());
    }
}