package com.sparta.ditto.feed.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

@Component
/**
 * JPA Auditing을 위한 현재 사용자 제공 구현체.
 * HTTP 요청의 X-User-Id 헤더에서 사용자 ID를 추출해 BaseEntity의 createdBy·updatedBy에 자동 주입한다.
 * 헤더가 없거나 파싱에 실패하면 Optional.empty()를 반환해 Auditing을 건너뛴다.
 */
public class AuditorAwareImpl implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        try {
            HttpServletRequest request = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            String userId = request.getHeader("X-User-Id");
            if (userId != null && !userId.isBlank()) {
                return Optional.of(UUID.fromString(userId));
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }
}