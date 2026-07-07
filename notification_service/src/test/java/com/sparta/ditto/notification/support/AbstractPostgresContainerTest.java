package com.sparta.ditto.notification.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Testcontainers PostgreSQL 싱글톤 컨테이너 베이스. 클래스마다 컨테이너를 새로 띄우지 않고
 * 하나를 JVM(Gradle 테스트 워커) 전체에서 재사용한다. 컨테이너는 static 초기화에서 1회만
 * 시작하고 명시적으로 stop하지 않으며(@Testcontainers/@Container 미사용), JVM 종료 시
 * Testcontainers Ryuk가 정리한다.
 */
public abstract class AbstractPostgresContainerTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}