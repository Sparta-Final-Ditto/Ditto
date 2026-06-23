package com.sparta.ditto.user.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.user.application.FollowService;
import com.sparta.ditto.user.presentation.dto.response.UserPublicProfileResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> follow(
            @RequestHeader("X-User-Id") UUID followerId,
            @PathVariable UUID userId) {
        followService.follow(followerId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(
            @RequestHeader("X-User-Id") UUID followerId,
            @PathVariable UUID userId) {
        followService.unfollow(followerId, userId);
        return ResponseEntity.ok(ApiResponse.deleted());
    }

    @GetMapping("/{userId}/followers")
    public ResponseEntity<ApiResponse<List<UserPublicProfileResponse>>> getFollowers(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(followService.getFollowers(userId)));
    }

    @GetMapping("/{userId}/followings")
    public ResponseEntity<ApiResponse<List<UserPublicProfileResponse>>> getFollowings(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(followService.getFollowings(userId)));
    }
}
