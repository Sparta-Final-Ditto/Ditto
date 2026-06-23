package com.sparta.ditto.chat.infrastructure.config;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
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
            // TODO: 공통 AuditorAware/JPA Auditing 설정이 제공되면 이 서비스 내부 구현은 제거하거나 교체한다.
        }
        return Optional.empty();
    }
}
