package com.sparta.ditto.feed.infrastructure.client.user.dto;

import java.util.List;
import java.util.UUID;

/**
 * user-service {@code GET /api/v1/users/me/blocks} 응답.
 * "내가 차단한 사용자" 목록이며, feed는 {@code data[].id}만 사용한다
 * (nickname/profileImageUrl/bio는 record에 선언하지 않아 Jackson이 무시).
 */
public record BlockedUsersResponse(
        int status,
        String message,
        List<BlockedUser> data
) {
    public record BlockedUser(UUID id) {}
}
