package com.sparta.ditto.user.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.user.application.BlockService;

import java.util.List;
import java.util.UUID;

import com.sparta.ditto.user.presentation.dto.response.UserPublicProfileResponse;
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
public class BlockController {

    private final BlockService blockService;

    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> block(
            @RequestHeader("X-User-Id") UUID blockerId,
            @PathVariable UUID userId) {
        blockService.block(blockerId, userId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @RequestHeader("X-User-Id") UUID blockerId,
            @PathVariable UUID userId) {
        blockService.unblock(blockerId, userId);
        return ResponseEntity.ok(ApiResponse.deleted());
    }

    @GetMapping("/me/blocks")
    public ResponseEntity<ApiResponse<List<UserPublicProfileResponse>>> getBlockedUsers(
            @RequestHeader("X-User-Id") UUID blockerId) {
        return ResponseEntity.ok(ApiResponse.success(blockService.getBlockedUsers(blockerId)));
    }
}
