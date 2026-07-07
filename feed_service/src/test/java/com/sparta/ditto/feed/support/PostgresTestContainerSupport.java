package com.sparta.ditto.feed.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL Testcontainers 싱글턴 베이스 클래스.
 *
 * <p>기존에는 각 통합테스트 클래스가 {@code @Testcontainers} + {@code @Container static}으로
 * 자체 컨테이너를 선언했다. 이 방식은 두 가지 문제를 만든다:
 * <ol>
 *   <li>클래스마다 컨테이너(포트)가 달라져 datasource URL이 바뀌고,
 *       Spring TestContext 캐시 키가 달라져 컨텍스트가 매번 새로 부팅된다.</li>
 *   <li>JUnit이 클래스 종료 시 컨테이너를 내리지만 Spring 컨텍스트(와 스케줄러 스레드,
 *       커넥션 풀)는 캐시에 살아남아 죽은 포트로 연결을 재시도한다.</li>
 * </ol>
 *
 * <p>이 클래스는 컨테이너를 JVM당 1회만 기동하고({@code static} 초기화 블록),
 * {@code @Container}를 붙이지 않아 JUnit이 클래스 단위로 내리지 않는다.
 * 컨테이너는 JVM 종료 시 Testcontainers의 Ryuk이 정리한다.
 *
 * <p>사용법: 통합테스트에서 {@code extends PostgresTestContainerSupport}만 하면 된다.
 * 개별 클래스에 {@code @Testcontainers}, {@code @Container},
 * datasource용 {@code @DynamicPropertySource}를 선언하지 말 것 —
 * 컨텍스트 캐시 키가 갈라져 이 클래스의 목적이 무력화된다.
 */
public abstract class PostgresTestContainerSupport {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
