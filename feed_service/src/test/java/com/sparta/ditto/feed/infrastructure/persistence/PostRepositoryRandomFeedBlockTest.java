package com.sparta.ditto.feed.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.support.PostgresTestContainerSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 랜덤 피드 차단 제외 쿼리(findFeedByVisibilityExcludingAuthorsWithCursor) 슬라이스 테스트.
 * 네이티브 쿼리가 PostgreSQL 전용이므로 Testcontainers PostgreSQL에서 실행하며,
 * 정렬/커서 검증을 결정적으로 만들기 위해 createdAt을 직접 세팅한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(PostRepositoryImpl.class)
class PostRepositoryRandomFeedBlockTest extends PostgresTestContainerSupport {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private TestEntityManager em;

    private static final Instant BASE = Instant.parse("2026-06-16T00:00:00Z");

    private Post savePost(UUID authorId, Visibility scope, Instant createdAt) {
        Post post = new Post(authorId, "작성자", "내용", "서울 강남구",
                37.4979, 127.0276, scope, true);
        ReflectionTestUtils.setField(post, "createdAt", createdAt);
        ReflectionTestUtils.setField(post, "updatedAt", createdAt);
        return em.persistAndFlush(post);
    }

    @Test
    @DisplayName("003-6: 내가 차단한 작성자 글은 제외되고, 차단 아닌 글만 최신순으로 조회된다")
    void excludesBlockedAuthorsPosts() {
        UUID blockedA = UUID.randomUUID();
        UUID blockedB = UUID.randomUUID();
        UUID normalC = UUID.randomUUID();

        savePost(blockedA, Visibility.PUBLIC, BASE.plusSeconds(10));
        Post visible1 = savePost(normalC, Visibility.PUBLIC, BASE.plusSeconds(20));
        savePost(blockedB, Visibility.PUBLIC, BASE.plusSeconds(30));
        Post visible2 = savePost(normalC, Visibility.PUBLIC, BASE.plusSeconds(40));
        savePost(normalC, Visibility.PRIVATE, BASE.plusSeconds(50)); // 공개범위 필터로 제외

        List<Post> result = postRepository.findFeedByVisibilityExcludingAuthorsWithCursor(
                List.of(Visibility.PUBLIC), List.of(blockedA, blockedB), null, null, 10);

        assertThat(result).extracting(Post::getId)
                .containsExactly(visible2.getId(), visible1.getId());
    }

    @ParameterizedTest(name = "003-14: excludeUserIds={0} → 필터 없이 전체 노출")
    @NullAndEmptySource
    @DisplayName("003-14: excludeUserIds가 null/빈 목록이면 NOT IN 빈 컬렉션 오류 없이 전체 노출")
    void nullOrEmptyExclude_returnsAll(List<UUID> excludeUserIds) {
        UUID authorA = UUID.randomUUID();
        UUID authorB = UUID.randomUUID();
        savePost(authorA, Visibility.PUBLIC, BASE.plusSeconds(10));
        savePost(authorB, Visibility.PUBLIC, BASE.plusSeconds(20));

        List<Post> unfiltered = postRepository.findFeedByVisibilityWithCursor(
                List.of(Visibility.PUBLIC), null, null, 10);
        List<Post> result = postRepository.findFeedByVisibilityExcludingAuthorsWithCursor(
                List.of(Visibility.PUBLIC), excludeUserIds, null, null, 10);

        assertThat(result).extracting(Post::getId)
                .containsExactlyElementsOf(unfiltered.stream().map(Post::getId).toList());
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("003-6 경계: 커서 경계에 차단 글이 걸려도 조회 전 제외되어 노출 글만 연속 페이징")
    void cursorSkipsBlockedAtBoundary() {
        UUID normal = UUID.randomUUID();
        UUID blocked = UUID.randomUUID();
        Post t1 = savePost(normal, Visibility.PUBLIC, BASE.plus(1, ChronoUnit.HOURS));
        Post t2 = savePost(normal, Visibility.PUBLIC, BASE.plus(2, ChronoUnit.HOURS));
        savePost(blocked, Visibility.PUBLIC, BASE.plus(3, ChronoUnit.HOURS)); // t3: 차단, 경계 위치
        Post t4 = savePost(normal, Visibility.PUBLIC, BASE.plus(4, ChronoUnit.HOURS));

        List<Post> firstPage = postRepository.findFeedByVisibilityExcludingAuthorsWithCursor(
                List.of(Visibility.PUBLIC), List.of(blocked), null, null, 2);
        assertThat(firstPage).extracting(Post::getId).containsExactly(t4.getId(), t2.getId());

        Post cursor = firstPage.get(1);
        List<Post> secondPage = postRepository.findFeedByVisibilityExcludingAuthorsWithCursor(
                List.of(Visibility.PUBLIC), List.of(blocked),
                cursor.getCreatedAt(), cursor.getId(), 2);
        assertThat(secondPage).extracting(Post::getId).containsExactly(t1.getId());
    }
}
