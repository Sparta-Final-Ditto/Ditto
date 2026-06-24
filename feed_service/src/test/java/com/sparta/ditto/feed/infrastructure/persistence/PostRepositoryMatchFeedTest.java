package com.sparta.ditto.feed.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
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
 * 매칭 피드 조회 쿼리(findFeedByUserIdsAndLocationScopeWithCursor) 슬라이스 테스트.
 * 네이티브 쿼리가 PostgreSQL 전용이므로 Testcontainers PostgreSQL에서 실행한다.
 * 페이징 순서를 결정적으로 만들기 위해 createdAt을 직접 세팅한다(JPA Auditing 미사용).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers
@Import(PostRepositoryImpl.class)
class PostRepositoryMatchFeedTest {

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

    /** createdAt을 명시적으로 지정해 정렬/커서 검증을 결정적으로 만든다. */
    private Post savePost(UUID authorId, LocationScope scope, Instant createdAt) {
        Post post = new Post(authorId, "추천유저", "내용", "서울 강남구",
                37.4979, 127.0276, scope, true);
        ReflectionTestUtils.setField(post, "createdAt", createdAt);
        ReflectionTestUtils.setField(post, "updatedAt", createdAt);
        return em.persistAndFlush(post);
    }

    @Test
    @DisplayName("005-5: 추천 유저의 게시글 중 PUBLIC만 조회되고 FOLLOWERS_ONLY/PRIVATE 및 비추천 유저 글은 제외된다")
    void findMatchFeed_includesOnlyPublicPostsOfRecommendedUsers() {
        // given
        UUID recommendedA = UUID.randomUUID();
        UUID recommendedB = UUID.randomUUID();
        UUID notRecommended = UUID.randomUUID();

        Post publicA = savePost(recommendedA, LocationScope.PUBLIC, BASE.plusSeconds(10));
        savePost(recommendedA, LocationScope.FOLLOWERS_ONLY, BASE.plusSeconds(20));
        savePost(recommendedB, LocationScope.PRIVATE, BASE.plusSeconds(30));
        Post publicB = savePost(recommendedB, LocationScope.PUBLIC, BASE.plusSeconds(40));
        savePost(notRecommended, LocationScope.PUBLIC, BASE.plusSeconds(50)); // 추천 안 된 유저

        // when
        List<Post> result = postRepository.findFeedByUserIdsAndLocationScopeWithCursor(
                List.of(recommendedA, recommendedB),
                List.of(LocationScope.PUBLIC),
                null, null, 10);

        // then
        assertThat(result).extracting(Post::getId)
                .containsExactlyInAnyOrder(publicA.getId(), publicB.getId());
    }

    @Test
    @DisplayName("005-8: cursor 기반 페이징이 created_at·id 내림차순으로 동작한다")
    void findMatchFeed_paginatesByCursor() {
        // given: 추천 유저의 PUBLIC 게시글 4개 (t1 < t2 < t3 < t4)
        UUID author = UUID.randomUUID();
        Post t1 = savePost(author, LocationScope.PUBLIC, BASE.plus(1, ChronoUnit.HOURS));
        Post t2 = savePost(author, LocationScope.PUBLIC, BASE.plus(2, ChronoUnit.HOURS));
        Post t3 = savePost(author, LocationScope.PUBLIC, BASE.plus(3, ChronoUnit.HOURS));
        Post t4 = savePost(author, LocationScope.PUBLIC, BASE.plus(4, ChronoUnit.HOURS));

        // when: 첫 페이지 2건 (최신순 desc)
        List<Post> firstPage = postRepository.findFeedByUserIdsAndLocationScopeWithCursor(
                List.of(author), List.of(LocationScope.PUBLIC), null, null, 2);

        // then: t4, t3
        assertThat(firstPage).extracting(Post::getId)
                .containsExactly(t4.getId(), t3.getId());

        // when: t3을 커서로 다음 페이지 2건
        Post cursor = firstPage.get(1);
        List<Post> secondPage = postRepository.findFeedByUserIdsAndLocationScopeWithCursor(
                List.of(author), List.of(LocationScope.PUBLIC),
                cursor.getCreatedAt(), cursor.getId(), 2);

        // then: t2, t1 (겹침 없음)
        assertThat(secondPage).extracting(Post::getId)
                .containsExactly(t2.getId(), t1.getId());
    }
}
