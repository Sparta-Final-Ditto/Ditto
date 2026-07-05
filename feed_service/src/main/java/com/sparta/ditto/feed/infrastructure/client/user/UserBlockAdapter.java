package com.sparta.ditto.feed.infrastructure.client.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.application.port.out.UserBlockPort;
import com.sparta.ditto.feed.infrastructure.client.user.dto.BlockedUsersResponse;
import com.sparta.ditto.feed.infrastructure.client.user.dto.ChatUserValidationRequest;
import feign.FeignException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link UserBlockPort} 구현체.
 *
 * <p>user-service의 {@code chat-validation}(checkBlock=true)을 호출해 양방향 차단을 검증한다.
 * 응답이 403 + code "BLOCK-004"이면 차단으로 판정(true)하고, 그 외 오류(4xx/5xx/타임아웃)는
 * 삼키지 않고 {@link FeignException}을 그대로 전파한다. fail-open 여부는 Application이 결정한다.</p>
 */
@Component
@RequiredArgsConstructor
public class UserBlockAdapter implements UserBlockPort {

    private static final int FORBIDDEN = 403;
    private static final String BLOCKED_CODE = "BLOCK-004";

    private final UserServiceClient userServiceClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<UUID> findBlockedUserIds(UUID requesterId) {
        BlockedUsersResponse response = userServiceClient.getMyBlocks(requesterId);
        return response.data() == null
                ? List.of()
                : response.data().stream()
                        .map(BlockedUsersResponse.BlockedUser::id)
                        .toList();
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
