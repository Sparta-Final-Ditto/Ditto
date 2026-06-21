package com.sparta.ditto.feed.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.feed.application.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.application.dto.response.CreatePostResponse;
import com.sparta.ditto.feed.application.dto.response.LikeResponse;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostCreateFacade postCreateFacade;
    private final PostInteractionService postInteractionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreatePostResponse>> createPost(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreatePostRequest request
    ) {
        CreatePostResponse response = postCreateFacade.createPost(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(response));
    }

    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> addLike(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID postId
    ) {
        LikeResponse response = postInteractionService.addLike(userId, postId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> removeLike(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID postId
    ) {
        LikeResponse response = postInteractionService.removeLike(userId, postId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
