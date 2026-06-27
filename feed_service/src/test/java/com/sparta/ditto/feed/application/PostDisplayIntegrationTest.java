package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"post-events"})
class PostDisplayIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("cloud.aws.credentials.access-key", () -> "test-key");
        registry.add("cloud.aws.credentials.secret-key", () -> "test-secret");
        registry.add("cloud.aws.region.static", () -> "ap-northeast-2");
        registry.add("cloud.aws.s3.bucket", () -> "test-bucket");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.data.redis.port", () -> "6379");
    }

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockBean
    private ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    private UUID savedPostId;

    @AfterEach
    void tearDown() {
        if (savedPostId != null) {
            postRepository.findById(savedPostId).ifPresent(p -> {
                // 테스트 격리: 저장된 게시글 삭제 (물리 삭제는 없으므로 soft delete)
                p.delete(p.getUserId());
                postRepository.save(p);
            });
        }
    }

    @Test
    @DisplayName("PUBLIC 게시글을 PRIVATE으로 변경하면 랜덤 피드에서 즉시 제외된다")
    void updateVisibility_toPrivate_excludedFromRandomFeed() throws Exception {
        // given: PUBLIC 게시글 저장
        UUID ownerId = UUID.randomUUID();
        Post post = postRepository.save(
                new Post(ownerId, "닉네임", "내용", "서울 성동구",
                        37.5, 127.0, Visibility.PUBLIC, true));
        savedPostId = post.getId();

        // when: PRIVATE으로 변경
        mockMvc.perform(patch("/api/v1/posts/{postId}/display", savedPostId)
                        .header("X-User-Id", ownerId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\": \"PRIVATE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("UPDATED"));

        // then: 랜덤 피드 조회 시 해당 게시글 미포함
        mockMvc.perform(get("/api/v1/feeds/random")
                        .header("X-User-Id", ownerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feeds[?(@.postId == '" + savedPostId + "')]").isEmpty());
    }
}