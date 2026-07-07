package com.sparta.ditto.feed.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.domain.type.MediaType;
import com.sparta.ditto.feed.infrastructure.persistence.PostRepositoryImpl;
import com.sparta.ditto.feed.support.PostgresTestContainerSupport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.ActiveProfiles;

/**
 * PostRepository.findByUserIdAndScopesWithCursor 슬라이스 테스트
 * - 이미지 2장 이상 → mediaList[0]의 sort_order=1 검증 (N+1 없이 eager fetch)
 * -PRIVATE 게시글은 PUBLIC 조회에서 제외
 * -FOLLOWERS_ONLY 게시글은 PUBLIC 조회에서 제외
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostRepositoryImpl.class)
class PostRepositoryUserPostsTest extends PostgresTestContainerSupport {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAwareImpl() {
            return Optional::empty;
        }
    }

    @Autowired
    private PostRepository postRepository;

    private final UUID authorId = UUID.randomUUID();

    private Post savePost(Visibility scope, String content) {
        Post post = new Post(authorId, "닉네임", content, "서울",
                37.5, 127.0, scope, true);
        return postRepository.save(post);
    }

    // -------------------------------------------------------
    // 이미지 2장 이상 → 첫 번째 미디어(sort_order=1)만 대표 이미지로 노출
    // -------------------------------------------------------
    @Test
    @DisplayName("이미지 2장 포함 게시글 조회 시 mediaList[0]의 sort_order=1이어야 한다")
    void findByUserIdAndScopesWithCursor_이미지2장_첫번째_sort_order_1() {
        // given
        Post post = savePost(Visibility.PUBLIC, "이미지 여러 장");
        post.addMedia(new PostMedia(post, "feeds/img1.jpg", MediaType.IMAGE, 1));
        post.addMedia(new PostMedia(post, "feeds/img2.jpg", MediaType.IMAGE, 2));
        postRepository.save(post);

        // when
        // findByUserIdAndScopesWithCursor: LEFT JOIN FETCH로 미디어를 sort_order ASC로 적재해야 한다
        List<Post> results = postRepository.findByUserIdAndScopesWithCursor(
                authorId, List.of(Visibility.PUBLIC), null, null, 10);

        // then
        assertThat(results).hasSize(1);
        List<PostMedia> mediaList = results.get(0).getMediaList();
        assertThat(mediaList).hasSizeGreaterThanOrEqualTo(2);
        assertThat(mediaList.get(0).getSortOrder()).isEqualTo(1);
        assertThat(mediaList.get(0).getS3Key()).isEqualTo("feeds/img1.jpg");
    }

    // -------------------------------------------------------
    // PRIVATE 게시글 → PUBLIC 스코프 조회 결과에서 제외
    // -------------------------------------------------------
    @Test
    @DisplayName("요청자가 작성자가 아닐 때(PUBLIC 조회) PRIVATE 게시글은 결과에서 제외된다")
    void findByUserIdAndScopesWithCursor_PRIVATE_게시글_제외() {
        // given
        savePost(Visibility.PUBLIC, "공개 게시글");
        savePost(Visibility.PRIVATE, "비공개 게시글");

        // when: 타인이 조회하는 상황 → PUBLIC만 허용
        List<Post> results = postRepository.findByUserIdAndScopesWithCursor(
                authorId, List.of(Visibility.PUBLIC), null, null, 10);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    // -------------------------------------------------------
    // FOLLOWERS_ONLY 게시글 → 팔로우하지 않은 경우(PUBLIC 조회) 결과에서 제외
    // -------------------------------------------------------
    @Test
    @DisplayName("요청자가 팔로우하지 않았을 때(PUBLIC 조회) FOLLOWERS_ONLY 게시글은 결과에서 제외된다")
    void findByUserIdAndScopesWithCursor_FOLLOWERS_ONLY_게시글_제외() {
        // given
        savePost(Visibility.PUBLIC, "공개 게시글");
        savePost(Visibility.FOLLOWERS_ONLY, "팔로워 전용 게시글");

        // when: 팔로우하지 않은 타인이 조회하는 상황 → PUBLIC만 허용
        List<Post> results = postRepository.findByUserIdAndScopesWithCursor(
                authorId, List.of(Visibility.PUBLIC), null, null, 10);

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getVisibility()).isEqualTo(Visibility.PUBLIC);
    }

    // -------------------------------------------------------
    // 본인 조회 시 PRIVATE 포함 전체 스코프 반환
    // -------------------------------------------------------
    @Test
    @DisplayName("본인 조회 시(모든 스코프 허용) PRIVATE 게시글도 결과에 포함된다")
    void findByUserIdAndScopesWithCursor_본인조회_PRIVATE_포함() {
        // given
        savePost(Visibility.PUBLIC, "공개 게시글");
        savePost(Visibility.PRIVATE, "비공개 게시글");
        savePost(Visibility.FOLLOWERS_ONLY, "팔로워 전용 게시글");

        // when: 본인 조회 → PUBLIC, FOLLOWERS_ONLY, PRIVATE 모두 허용
        List<Post> results = postRepository.findByUserIdAndScopesWithCursor(
                authorId,
                List.of(Visibility.PUBLIC, Visibility.FOLLOWERS_ONLY, Visibility.PRIVATE),
                null, null, 10);

        // then
        assertThat(results).hasSize(3);
    }
}