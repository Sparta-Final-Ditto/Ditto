package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.LocationScope;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"post-events"})
class PostDetailIntegrationTest {

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

    private final UUID userId = UUID.randomUUID();

    private Post savedPost() {
        return postRepository.save(new Post(
                userId, "닉네임", "내용", "강남구",
                37.5, 127.0, LocationScope.PUBLIC, true));
    }

    @Test
    @DisplayName("응답 본문에 isMyPost, postId가 포함된다")
    void getPostDetail_응답_필수필드_포함() throws Exception {
        Post post = savedPost();

        mockMvc.perform(get("/posts/{postId}", post.getId())
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isMyPost").value(true))
                .andExpect(jsonPath("$.data.postId").value(post.getId().toString()))
                .andExpect(jsonPath("$.data.viewCount").doesNotExist())
                .andExpect(jsonPath("$.data.latitude").doesNotExist())
                .andExpect(jsonPath("$.data.longitude").doesNotExist());
    }

    @Test
    @DisplayName("존재하지 않는 postId 조회 시 404 POST_NOT_FOUND")
    void getPostDetail_없는게시글_404() throws Exception {
        mockMvc.perform(get("/posts/{postId}", UUID.randomUUID())
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }
}