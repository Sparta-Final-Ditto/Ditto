package com.sparta.ditto.feed.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화 설정.
 *
 * <p>{@code @EnableScheduling}을 애플리케이션 클래스가 아닌 별도 설정으로 분리하여
 * {@code app.scheduling.enabled} 프로퍼티로 켜고 끌 수 있게 한다.
 *
 * <ul>
 *   <li>운영/로컬: 프로퍼티 미설정 시 기본 활성화({@code matchIfMissing = true})</li>
 *   <li>테스트: application-test.yml에서 {@code app.scheduling.enabled: false}로 비활성화.
 *       통합테스트 컨텍스트가 캐시에 남아 있는 동안 스케줄러 스레드가
 *       이미 종료된 Testcontainers DB로 커넥션을 시도하는 문제를 방지한다.</li>
 *   <li>스케줄러 자체를 검증하는 테스트는
 *       {@code @TestPropertySource(properties = "app.scheduling.enabled=true")}로
 *       개별 활성화할 수 있다.</li>
 * </ul>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "app.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SchedulingConfig {
}
