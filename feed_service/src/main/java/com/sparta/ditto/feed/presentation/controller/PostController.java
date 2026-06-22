package com.sparta.ditto.feed.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.feed.application.dto.CommentResult;
import com.sparta.ditto.feed.application.dto.CreateCommentCommand;
import com.sparta.ditto.feed.application.dto.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.GetLikesQuery;
import com.sparta.ditto.feed.application.dto.LikeListResult;
import com.sparta.ditto.feed.application.dto.LikeResult;
import com.sparta.ditto.feed.application.dto.PostResult;
import com.sparta.ditto.feed.presentation.dto.request.CreateCommentRequest;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.presentation.dto.response.CommentResponse;
import com.sparta.ditto.feed.presentation.dto.response.CreatePostResponse;
import com.sparta.ditto.feed.presentation.dto.response.LikeListResponse;
import com.sparta.ditto.feed.presentation.dto.response.LikeResponse;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostCreateFacade postCreateFacade;
    private final PostInteractionService postInteractionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreatePostResponse>> createPost(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Nickname") String nickname,
            @Valid @RequestBody CreatePostRequest request
    ) {
        List<CreatePostCommand.MediaFileItem> mediaFileItems = request.mediaFiles() != null
                ? request.mediaFiles().stream()
                        .map(m -> new CreatePostCommand.MediaFileItem(m.s3Key(), m.mediaType(), m.sortOrder()))
                        .toList()
                : List.of();
        CreatePostCommand command = new CreatePostCommand(
                userId,
                nickname,
                request.content(),
                request.tags(),
                request.latitude(),
                request.longitude(),
                request.locationScope(),
                request.showLocation(),
                mediaFileItems
        );
        PostResult result = postCreateFacade.createPost(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(CreatePostResponse.from(result)));
    }

    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> addLike(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID postId
    ) {
        LikeResult result = postInteractionService.addLike(userId, postId);
        return ResponseEntity.ok(ApiResponse.success(LikeResponse.from(result)));
    }

    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> removeLike(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID postId
    ) {
        LikeResult result = postInteractionService.removeLike(userId, postId);
        return ResponseEntity.ok(ApiResponse.success(LikeResponse.from(result)));
    }

    @GetMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeListResponse>> getLikes(
            @PathVariable UUID postId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size
    ) {
        LikeListResult result = postInteractionService.getLikes(new GetLikesQuery(postId, cursor, size));
        return ResponseEntity.ok(ApiResponse.success(LikeListResponse.from(result)));
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Nickname") String nickname,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        CommentResult result = postInteractionService.createComment(
                userId, nickname, postId, new CreateCommentCommand(request.content())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(CommentResponse.from(result)));
    }
}
