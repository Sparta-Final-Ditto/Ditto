package com.sparta.ditto.feed.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.application.service.PostHardDeleteService;
import com.sparta.ditto.feed.domain.entity.Comment;
import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.domain.entity.OutboxEvent;
import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.entity.PostMedia;
import com.sparta.ditto.feed.domain.entity.PostTag;
import com.sparta.ditto.feed.domain.type.MediaType;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.infrastructure.kafka.OutboxEventAdapter;
import com.sparta.ditto.feed.infrastructure.persistence.CommentJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.CommentRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.LikeJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.LikeRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.OutboxEventJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.OutboxEventRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.PostJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.PostMediaJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.PostMediaRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.PostRepositoryImpl;
import com.sparta.ditto.feed.infrastructure.persistence.PostTagJpaRepository;
import com.sparta.ditto.feed.infrastructure.persistence.PostTagRepositoryImpl;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * PostHardDeleteService 슬라이스 통합 테스트.
 *
 * <p>@DataJpaTest는 JPA 슬라이스만 로드하므로 @Service, @Repository 빈은 자동으로 등록되지 않는다.
 * PostHardDeleteService와 각 RepositoryImpl을 @Import로 명시하여
 * 도메인 인터페이스(PostRepository 등)를 통한 실제 DIP 구현체가 동작하도록 한다.
 * 데이터 셋업·검증에는 @DataJpaTest가 자동 등록하는 JpaRepository 구현체를 직접 사용한다.
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
    PostHardDeleteService.class,
    PostRepositoryImpl.class,
    CommentRepositoryImpl.class,
    LikeRepositoryImpl.class,
    PostMediaRepositoryImpl.class,
    PostTagRepositoryImpl.class,
    OutboxEventRepositoryImpl.class,
    OutboxEventAdapter.class
})
class PostHardDeleteServiceTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAwareImpl() {
            return Optional::empty;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PostHardDeleteService postHardDeleteService;

    // 데이터 셋업·검증용 JpaRepository (DataJpaTest가 자동 등록)
    @Autowired
    private PostJpaRepository postJpaRepository;
    @Autowired
    private CommentJpaRepository commentJpaRepository;
    @Autowired
    private LikeJpaRepository likeJpaRepository;
    @Autowired
    private PostMediaJpaRepository postMediaJpaRepository;
    @Autowired
    private PostTagJpaRepository postTagJpaRepository;
    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    private UUID savedPostId;

    @BeforeEach
    void setUp() {
        outboxEventJpaRepository.deleteAll();

        Post post = new Post(
                UUID.randomUUID(), "닉네임", "내용", "서울 강남구",
                37.5, 127.0, Visibility.PUBLIC, true);
        Post savedPost = postJpaRepository.save(post);
        savedPostId = savedPost.getId();

        // post_media, post_tags — FK to posts (ON DELETE NO ACTION)
        postMediaJpaRepository.save(
                new PostMedia(savedPost, "feeds/test-image.jpg", MediaType.IMAGE, 1));
        postTagJpaRepository.save(new PostTag(savedPost, "여행"));

        // comments, likes — FK 없음 (UUID 컬럼만)
        commentJpaRepository.save(
                new Comment(savedPostId, UUID.randomUUID(), "닉네임", "테스트 댓글"));
        likeJpaRepository.save(
                new Like(savedPostId, UUID.randomUUID(), "닉네임"));
    }

    @Test
    @DisplayName("purgePost 후 posts·comments·likes·post_media·post_tags 행이 모두 물리 삭제됨")
    void purgePost_모든_연관행_물리삭제() {
        // when
        postHardDeleteService.purgePost(savedPostId);

        // then — soft delete(deletedAt 설정)가 아닌 실제 row 제거 확인
        assertThat(postJpaRepository.existsById(savedPostId)).isFalse();
        assertThat(commentJpaRepository.count()).isZero();
        assertThat(likeJpaRepository.count()).isZero();
        assertThat(postMediaJpaRepository.count()).isZero();
        assertThat(postTagJpaRepository.count()).isZero();
    }

    @Test
    @DisplayName("댓글·좋아요·미디어·태그 모두 존재해도 FK 위반 없이 삭제 완료")
    void purgePost_FK위반_없이_삭제순서_보장() {
        // given — setUp에서 post_media·post_tags(FK NO ACTION)·comments·likes가 이미 저장됨

        // when & then — DataIntegrityViolationException 발생 없이 완료
        assertDoesNotThrow(() -> postHardDeleteService.purgePost(savedPostId));
        assertThat(postJpaRepository.existsById(savedPostId)).isFalse();
    }

    @Test
    @DisplayName("purgePost 호출 시 POST_HARD_DELETED 이벤트가 1건 저장되며 payload에 postId·authorId가 포함됨")
    void purgePost_POST_HARD_DELETED_이벤트_저장() throws Exception {
        // given — soft delete된 상태 재현 (스케줄러는 soft-deleted post만 처리)
        Post post = postJpaRepository.findById(savedPostId).orElseThrow();
        UUID softDeletedBy = UUID.randomUUID();
        post.delete(softDeletedBy);
        postJpaRepository.save(post);

        // when
        postHardDeleteService.purgePost(savedPostId);

        // then
        List<OutboxEvent> events = outboxEventJpaRepository.findAll();
        assertThat(events).hasSize(1);

        OutboxEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("POST_HARD_DELETED");
        assertThat(event.getTopic()).isEqualTo("post-events");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode payload = mapper.readTree(event.getPayload());
        assertThat(payload.get("postId").asText()).isEqualTo(savedPostId.toString());
        assertThat(payload.get("authorId").asText()).isEqualTo(post.getUserId().toString());
    }
}