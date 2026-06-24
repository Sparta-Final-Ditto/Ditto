package com.sparta.ditto.feed.presentation.controller;

import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.feed.application.dto.GetUserPostsQuery;
import com.sparta.ditto.feed.application.dto.PostDetailResult;
import com.sparta.ditto.feed.application.dto.UserPostsResult;
import com.sparta.ditto.feed.application.dto.command.CreateCommentCommand;
import com.sparta.ditto.feed.application.dto.command.CreatePostCommand;
import com.sparta.ditto.feed.application.dto.query.GetCommentsQuery;
import com.sparta.ditto.feed.application.dto.query.GetLikesQuery;
import com.sparta.ditto.feed.application.dto.result.CommentListResult;
import com.sparta.ditto.feed.application.dto.result.CommentResult;
import com.sparta.ditto.feed.application.dto.result.LikeListResult;
import com.sparta.ditto.feed.application.dto.result.LikeResult;
import com.sparta.ditto.feed.application.dto.result.PostResult;
import com.sparta.ditto.feed.application.facade.PostCreateFacade;
import com.sparta.ditto.feed.application.service.PostInteractionService;
import com.sparta.ditto.feed.application.service.PostService;
import com.sparta.ditto.feed.presentation.dto.request.CreateCommentRequest;
import com.sparta.ditto.feed.presentation.dto.request.CreatePostRequest;
import com.sparta.ditto.feed.presentation.dto.response.CommentListResponse;
import com.sparta.ditto.feed.presentation.dto.response.CommentResponse;
import com.sparta.ditto.feed.presentation.dto.response.CreatePostResponse;
import com.sparta.ditto.feed.presentation.dto.response.LikeListResponse;
import com.sparta.ditto.feed.presentation.dto.response.LikeResponse;
import com.sparta.ditto.feed.presentation.dto.response.PostDetailResponse;
import com.sparta.ditto.feed.presentation.dto.response.UserPostsResponse;
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
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostCreateFacade postCreateFacade;
    private final PostInteractionService postInteractionService;
    private final PostService postService;

    // -------------------------------------------------------
    // 3*3 게시글 목록 조회
    // -------------------------------------------------------
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserPostsResponse>> getUserPosts(
            @RequestHeader("X-User-Id") UUID requesterId,
            @PathVariable UUID userId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "21") @Min(1) @Max(21) int size
    ) {
        UserPostsResult result = postService.getUserPosts(
                new GetUserPostsQuery(requesterId, userId, cursor, size));
        return ResponseEntity.ok(ApiResponse.success(UserPostsResponse.from(result)));
    }

    // -------------------------------------------------------
    // 게시글 조회
    // -------------------------------------------------------
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getPostDetail(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable UUID postId
    ) {
        PostDetailResult result = postService.getPostDetail(postId, userId);
        return ResponseEntity.ok(ApiResponse.success(PostDetailResponse.from(result)));
    }

    // -------------------------------------------------------
    // 게시글 생성
    // -------------------------------------------------------
    @PostMapping
    public ResponseEntity<ApiResponse<CreatePostResponse>> createPost(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Nickname") String nickname,
            @Valid @RequestBody CreatePostRequest request
    ) {
        List<CreatePostCommand.MediaFileItem> mediaFileItems = request.mediaFiles() != null
                ? request.mediaFiles().stream()
                        .map(m -> new CreatePostCommand.MediaFileItem(
                                m.s3Key(), m.mediaType(), m.sortOrder()))
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(CreatePostResponse.from(result)));
    }

    // -------------------------------------------------------
    // 좋아요 생성
    // -------------------------------------------------------
    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> addLike(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Nickname") String nickname,
            @PathVariable UUID postId
    ) {
        LikeResult result = postInteractionService.addLike(userId, postId, nickname);
        return ResponseEntity.ok(ApiResponse.success(LikeResponse.from(result)));
    }

    // -------------------------------------------------------
    // 좋아요 삭제
    // -------------------------------------------------------
    @DeleteMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeResponse>> removeLike(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID postId
    ) {
        LikeResult result = postInteractionService.removeLike(userId, postId);
        return ResponseEntity.ok(ApiResponse.success(LikeResponse.from(result)));
    }

    // -------------------------------------------------------
    // 댓글 목록 조회
    // -------------------------------------------------------
    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentListResponse>> getComments(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable UUID postId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size
    ) {
        CommentListResult result = postInteractionService.getComments(
                new GetCommentsQuery(postId, userId, userRole, cursor, size));
        return ResponseEntity.ok(ApiResponse.success(CommentListResponse.from(result)));
    }

    // -------------------------------------------------------
    // 좋아요 목록 조회
    // -------------------------------------------------------
    @GetMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<LikeListResponse>> getLikes(
            @PathVariable UUID postId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size
    ) {
        LikeListResult result = postInteractionService.getLikes(
                new GetLikesQuery(postId, cursor, size));
        return ResponseEntity.ok(ApiResponse.success(LikeListResponse.from(result)));
    }

    // -------------------------------------------------------
    // 게시글 삭제
    // -------------------------------------------------------
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable UUID postId
    ) {
        postService.deletePost(postId, userId, userRole);
        return ResponseEntity.ok(ApiResponse.deleted());
    }

    // -------------------------------------------------------
    // 댓글 삭제
    // -------------------------------------------------------
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole,
            @PathVariable UUID postId,
            @PathVariable UUID commentId
    ) {
        postInteractionService.deleteComment(userId, userRole, postId, commentId);
        return ResponseEntity.ok(ApiResponse.success());
    }

    // -------------------------------------------------------
    // 댓글 생성
    // -------------------------------------------------------
    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Nickname") String nickname,
            @PathVariable UUID postId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        CommentResult result = postInteractionService.createComment(
                userId, nickname, postId, new CreateCommentCommand(request.content()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(CommentResponse.from(result)));
    }
}
