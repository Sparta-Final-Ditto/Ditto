package com.sparta.ditto.feed.infrastructure.client.user;

import com.sparta.ditto.feed.infrastructure.client.user.dto.BlockedUsersResponse;
import com.sparta.ditto.feed.infrastructure.client.user.dto.ChatUserValidationRequest;
import com.sparta.ditto.feed.infrastructure.client.user.dto.FollowingResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service", url = "${app.user-service.base-url}")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{userId}/followings")
    FollowingResponse getFollowings(@PathVariable UUID userId);

    /**
     * 차단 관계 검증(양방향). 차단이면 user-service가 403(BLOCK-004)을 응답하여 FeignException이 발생한다.
     */
    @PostMapping("/api/v1/internal/users/chat-validation")
    void validateChatUsers(@RequestBody ChatUserValidationRequest request);

    /**
     * 요청자가 차단한 사용자 목록(피드 단방향 필터용). 팔로우 피드와 동일하게 user-service 외부 API를 재사용한다.
     */
    @GetMapping("/api/v1/users/me/blocks")
    BlockedUsersResponse getMyBlocks(@RequestHeader("X-User-Id") UUID userId);
}
