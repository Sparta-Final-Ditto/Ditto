package com.sparta.ditto.match.infrastructure.feign;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.match.application.dto.UserNeighborhoodDto;
import com.sparta.ditto.match.application.dto.UserPublicProfileDto;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "user-service", url = "${feign.client.config.user-service.url}")
public interface UserServiceClient {

    // 팔로잉 목록
    @GetMapping("/api/v1/users/{userId}/followings")
    ApiResponse<List<UserPublicProfileDto>> getFollowings(
            @PathVariable UUID userId
    );

    // 차단 유저 목록
    @GetMapping("/api/v1/users/me/blocks")
    ApiResponse<List<UserPublicProfileDto>> getBlockedUsers(
            @RequestHeader("X-User-Id") UUID userId
    );

    // 내 프로필 조회 (동네 정보 포함)
    @GetMapping("/api/v1/users/me")
    ApiResponse<UserNeighborhoodDto> getMyProfile(
            @RequestHeader("X-User-Id") UUID userId
    );
}
