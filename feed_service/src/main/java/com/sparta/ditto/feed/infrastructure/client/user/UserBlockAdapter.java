package com.sparta.ditto.feed.infrastructure.client.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.application.port.out.UserBlockPort;
import com.sparta.ditto.feed.infrastructure.client.user.dto.BlockRelationsResponse;
import com.sparta.ditto.feed.infrastructure.client.user.dto.ChatUserValidationRequest;
import feign.FeignException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link UserBlockPort} 구현체.
 *
 * <p>좋아요·댓글용 차단 검증은 user-service의 {@code chat-validation}(checkBlock=true)을 호출하며,
 * 응답이 403 + code "BLOCK-004"이면 차단으로 판정(true)한다. 피드용 차단 관계 목록은
 * {@code block-relations}를 호출해 {@code blockedUserIds}(내가 차단) ∪ {@code blockedByUserIds}
 * (나를 차단)를 union(중복 제거)한 단일 목록으로 반환한다(양방향). 어느 경우든 오류(4xx/5xx/타임아웃)는
 * 삼키지 않고 {@link FeignException}을 그대로 전파하며, fail-open 여부는 Application이 결정한다.</p>
 */
@Component
@RequiredArgsConstructor
public class UserBlockAdapter implements UserBlockPort {

    private static final int FORBIDDEN = 403;
    private static final String BLOCKED_CODE = "BLOCK-004";

    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<UUID> findBlockRelationUserIds(UUID requesterId) {
        BlockRelationsResponse.Data data =
                userServiceClient.getBlockRelations(requesterId).data();
        if (data == null) {
            return List.of();
        }
        return Stream.concat(
                        safe(data.blockedUserIds()).stream(),
                        safe(data.blockedByUserIds()).stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private static List<UUID> safe(List<UUID> ids) {
        return ids == null ? List.of() : ids;
    }

    @Override
    public boolean isBlockedEitherDirection(UUID requesterId, UUID targetUserId) {
        try {
            userServiceClient.validateChatUsers(
                    ChatUserValidationRequest.ofBlockCheck(requesterId, targetUserId));
            return false;
        } catch (FeignException e) {
            if (isBlockError(e)) {
                return true;
            }
            throw e;
        }
    }

    private boolean isBlockError(FeignException e) {
        if (e.status() != FORBIDDEN) {
            return false;
        }
        String body = e.contentUTF8();
        if (body == null || body.isBlank()) {
            return false;
        }
        try {
            return BLOCKED_CODE.equals(objectMapper.readTree(body).path("code").asText());
        } catch (JsonProcessingException ex) {
            return false;
        }
    }
}
