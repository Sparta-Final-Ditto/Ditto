package com.sparta.ditto.feed.application;

import com.sparta.ditto.feed.domain.entity.Post;
import com.sparta.ditto.feed.domain.repository.PostRepository;
import com.sparta.ditto.feed.domain.type.Visibility;
import com.sparta.ditto.feed.support.PostgresTestContainerSupport;
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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"post-events"})
class PostDetailIntegrationTest extends PostgresTestContainerSupport {

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
                37.5, 127.0, Visibility.PUBLIC, true));
    }

    @Test
    @DisplayName("응답 본문에 isMyPost, postId가 포함된다")
    void getPostDetail_응답_필수필드_포함() throws Exception {
        Post post = savedPost();

        mockMvc.perform(get("/api/v1/posts/{postId}", post.getId())
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
        mockMvc.perform(get("/api/v1/posts/{postId}", UUID.randomUUID())
                        .header("X-User-Id", userId)
                        .header("X-User-Role", "USER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }
}