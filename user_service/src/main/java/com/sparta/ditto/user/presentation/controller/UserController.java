package com.sparta.ditto.user.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.user.application.UserService;
import com.sparta.ditto.user.presentation.dto.request.PasswordChangeRequest;
import com.sparta.ditto.user.presentation.dto.request.UserUpdateRequest;
import com.sparta.ditto.user.presentation.dto.response.AuthTokenResponse;
import com.sparta.ditto.user.presentation.dto.response.UserUpdateResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserUpdateResponse>> updateProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.updated(userService.updateProfile(userId, request)));
    }

    @PostMapping("/me/password")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> changePassword(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody PasswordChangeRequest request) {
        return ResponseEntity.ok(ApiResponse.updated(userService.changePassword(userId, request)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @RequestHeader("X-User-Id") UUID userId) {
        userService.deleteAccount(userId);
        return ResponseEntity.ok(ApiResponse.deleted());
    }
}
