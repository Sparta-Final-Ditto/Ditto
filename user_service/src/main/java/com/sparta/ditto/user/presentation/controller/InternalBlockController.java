package com.sparta.ditto.user.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.user.application.BlockService;
import com.sparta.ditto.user.presentation.dto.response.BlockRelationsResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/users")
@RequiredArgsConstructor
public class InternalBlockController {

    private final BlockService blockService;

    @GetMapping("/{userId}/block-relations")
    public ResponseEntity<ApiResponse<BlockRelationsResponse>> getBlockRelations(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(blockService.getBlockRelations(userId)));
    }
}
