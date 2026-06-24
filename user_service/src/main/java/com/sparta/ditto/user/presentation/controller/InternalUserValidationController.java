package com.sparta.ditto.user.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.user.application.UserService;
import com.sparta.ditto.user.presentation.dto.request.InternalChatUserValidationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalUserValidationController {

    private final UserService userService;

    @PostMapping("/chat-validation")
    public ResponseEntity<ApiResponse<Void>> validateChatUsers(
            @Valid @RequestBody InternalChatUserValidationRequest request
    ) {
        userService.validateChatUsers(
                request.requesterId(),
                request.targetUserIds(),
                request.checkBlock()
        );
        return ResponseEntity.ok(ApiResponse.success());
    }
}
