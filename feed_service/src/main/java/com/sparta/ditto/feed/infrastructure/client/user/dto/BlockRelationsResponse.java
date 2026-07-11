package com.sparta.ditto.feed.infrastructure.client.user.dto;

import java.util.List;
import java.util.UUID;

/**
 * user-service {@code GET /api/v1/internal/users/{userId}/block-relations} 응답(양방향).
 *
 * <p>{@code data.blockedUserIds}는 내가 차단한 사용자, {@code data.blockedByUserIds}는
 * 나를 차단한 사용자 ID 목록이다. feed-service는 두 목록을 union(중복 제거)하여
 * 단일 차단 관계 ID 목록으로 사용한다(API_SPEC 2.16).</p>
 */
public record BlockRelationsResponse(
        int status,
        String message,
        Data data
) {
    public record Data(List<UUID> blockedUserIds, List<UUID> blockedByUserIds) {}
}