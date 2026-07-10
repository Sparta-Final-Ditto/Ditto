package com.sparta.ditto.feed.application.service;

import com.sparta.ditto.feed.application.port.out.UserBlockPort;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 차단 검증 전용 Application 서비스.
 *
 * <p>차단 조회(Feign I/O)에만 Resilience4j(CircuitBreaker + Retry, 기존 {@code userServiceClient}
 * 인스턴스 재사용)를 적용한다. 이 메서드는 <b>boolean만 반환</b>하며 차단 시 예외를 던지지 않는다.
 * 차단(true)을 상호작용 거부(예외)로 변환하는 정책은 CB 어노테이션 밖(Facade)에서 수행한다.</p>
 *
 * <p>fallback은 fail-open: 조회 실패(타임아웃/5xx/서킷 OPEN=CallNotPermittedException)를 모두
 * "차단 없음"({@code false})으로 간주한다.</p>
 */
@Service
@RequiredArgsConstructor
public class BlockCheckService {

    private final UserBlockPort userBlockPort;

    @CircuitBreaker(name = "userServiceClient", fallbackMethod = "failOpen")
    @Retry(name = "userServiceClient")
    public boolean isBlockedEitherDirection(UUID requesterId, UUID targetUserId) {
        return userBlockPort.isBlockedEitherDirection(requesterId, targetUserId);
    }

    @SuppressWarnings("unused")
    private boolean failOpen(UUID requesterId, UUID targetUserId, Throwable t) {
        return false;
    }

    /**
     * 피드 양방향 필터용: 요청자의 차단 관계 사용자 ID 목록(내가 차단 ∪ 나를 차단)을 조회한다.
     * fallback은 fail-open: 조회 실패(타임아웃/5xx/서킷 OPEN) 시 빈 목록을 반환한다.
     */
    @CircuitBreaker(name = "userServiceClient", fallbackMethod = "emptyBlockedList")
    @Retry(name = "userServiceClient")
    public List<UUID> blockedUserIds(UUID requesterId) {
        return userBlockPort.findBlockRelationUserIds(requesterId);
    }

    @SuppressWarnings("unused")
    private List<UUID> emptyBlockedList(UUID requesterId, Throwable t) {
        return List.of();
    }
}
