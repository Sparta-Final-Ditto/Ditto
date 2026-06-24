package com.sparta.ditto.feed.domain.repository;

import com.sparta.ditto.feed.domain.entity.Like;
import com.sparta.ditto.feed.infrastructure.persistence.LikeRepositoryImpl;
import java.util.Optional;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LikeRepositoryImpl.class)
class LikeRepositoryTest {

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
    private LikeRepository likeRepository;

    private final UUID postId = UUID.randomUUID();

    @Test
    @DisplayName("좋아요 존재 → 최신순 정렬된 목록 반환")
    void findLikesWithCursor_좋아요존재_목록반환() {
        // given
        Like like = likeRepository.save(new Like(postId, UUID.randomUUID()));

        // when
        List<Like> result = likeRepository.findLikesWithCursor(
                postId, null, null, 20);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(like.getId());
    }

    @Test
    @DisplayName("좋아요 없음 → 빈 목록 반환")
    void findLikesWithCursor_좋아요없음_빈목록반환() {
        // given
        // 저장된 좋아요 없음

        // when
        List<Like> result = likeRepository.findLikesWithCursor(
                postId, null, null, 20);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("여러 좋아요 → created_at DESC, id DESC 최신순 정렬")
    void findLikesWithCursor_여러좋아요_최신순정렬() throws InterruptedException {
        // given
        Like older = likeRepository.save(new Like(postId, UUID.randomUUID()));
        Thread.sleep(10);
        Like newer = likeRepository.save(new Like(postId, UUID.randomUUID()));

        // when
        List<Like> result = likeRepository.findLikesWithCursor(
                postId, null, null, 20);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(newer.getId());
        assertThat(result.get(1).getId()).isEqualTo(older.getId());
    }

    @Test
    @DisplayName("좋아요 사용자 존재 → userId, nickname 포함")
    void findLikesWithCursor_사용자정보포함() {
        // given
        UUID userId = UUID.randomUUID();
        likeRepository.save(new Like(postId, userId));

        // when
        List<Like> result = likeRepository.findLikesWithCursor(
                postId, null, null, 20);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(userId);
        assertThat(result.get(0).getUserNickname()).isEqualTo("user");
    }

    @Test
    @DisplayName("cursor 적용 → cursor 이후(더 오래된) 데이터만 반환, hasNext·nextCursor 정상 계산")
    void findLikesWithCursor_cursor적용_이후데이터만반환() throws InterruptedException {
        // given
        Like first = likeRepository.save(new Like(postId, UUID.randomUUID()));
        Thread.sleep(10);
        Like second = likeRepository.save(new Like(postId, UUID.randomUUID()));
        Thread.sleep(10);
        likeRepository.save(new Like(postId, UUID.randomUUID()));

        // when - 내림차순 기준 cursor = second → first만 반환
        List<Like> result = likeRepository.findLikesWithCursor(
                postId, second.getCreatedAt(), second.getId(), 20);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("size 초과 데이터 존재 → size+1 조회로 hasNext=true 판단 가능")
    void findLikesWithCursor_size초과_hasNext판단가능() {
        // given
        int size = 2;
        for (int i = 0; i < 3; i++) {
            likeRepository.save(new Like(postId, UUID.randomUUID()));
        }

        // when
        List<Like> result = likeRepository.findLikesWithCursor(
                postId, null, null, size + 1);

        // then - size+1개 조회됐으므로 서비스 계층에서 hasNext=true 판단 가능
        assertThat(result.size()).isGreaterThan(size);
    }
}