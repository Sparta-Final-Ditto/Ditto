package com.sparta.ditto.feed.infrastructure.client.user.dto;

import java.util.List;
import java.util.UUID;

/**
 * user-service {@code POST /api/v1/internal/users/chat-validation} 요청 본문.
 * 좋아요·댓글 차단 검증에 재사용하며, {@code checkBlock=true}로 양방향 차단을 검사한다.
 */
public record ChatUserValidationRequest(
        UUID requesterId,
        List<UUID> targetUserIds,
        boolean checkBlock
) {
    public static ChatUserValidationRequest ofBlockCheck(UUID requesterId, UUID targetUserId) {
        return new ChatUserValidationRequest(requesterId, List.of(targetUserId), true);
    }
}
