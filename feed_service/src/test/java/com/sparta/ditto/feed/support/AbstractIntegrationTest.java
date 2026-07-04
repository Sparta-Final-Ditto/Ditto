package com.sparta.ditto.feed.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * MockMvc 기반 통합 테스트 공통 베이스.
 *
 * <p>{@code @SpringBootTest} + {@code @AutoConfigureMockMvc} + {@code @ActiveProfiles("test")} +
 * {@code @EmbeddedKafka} + Redis 인프라 모킹 + {@link PostgresTestContainerSupport}(싱글턴 컨테이너)를
 * 한곳에 모아 <b>Spring TestContext 캐시 키를 통일</b>한다. 동일한 공통 설정을 상속하는 통합 테스트끼리
 * 컨텍스트를 재사용(캐싱)하여 부팅 비용을 줄인다(TEST_CASES 2장 ① 참조).</p>
 *
 * <p>테스트별 추가 모킹은 서브클래스에서 {@code @MockitoBean}으로 선언한다.
 * ({@code @MockBean}은 deprecated이므로 신규 테스트는 {@code @MockitoBean}만 사용한다.)</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"post-events"})
public abstract class AbstractIntegrationTest extends PostgresTestContainerSupport {

    @MockitoBean
    protected RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    protected ReactiveRedisConnectionFactory reactiveRedisConnectionFactory;
}