package com.sparta.ditto.feed.infrastructure.client.user;

import com.sparta.ditto.feed.infrastructure.client.user.dto.BlockRelationsResponse;
import com.sparta.ditto.feed.infrastructure.client.user.dto.ChatUserValidationRequest;
import com.sparta.ditto.feed.infrastructure.client.user.dto.FollowingResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

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
     * 요청자의 양방향 차단 관계(내가 차단한 사용자 ∪ 나를 차단한 사용자)를 조회한다(피드 양방향 필터용).
     * user-service internal API를 Feign 직통 호출한다(API_SPEC 2.16).
     */
    @GetMapping("/api/v1/internal/users/{userId}/block-relations")
    BlockRelationsResponse getBlockRelations(@PathVariable UUID userId);
}
