package com.sparta.ditto.notification.presentation.controller;

import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import com.sparta.ditto.common.response.ApiResponse;
import com.sparta.ditto.notification.application.NotificationService;
import com.sparta.ditto.notification.application.SseService;
import com.sparta.ditto.notification.application.dto.NotificationListResult;
import com.sparta.ditto.notification.application.dto.ReadByRoomResult;
import com.sparta.ditto.notification.application.dto.ReadNotificationResult;
import com.sparta.ditto.notification.domain.exception.RoomIdRequiredException;
import com.sparta.ditto.notification.presentation.dto.request.ReadByRoomRequest;
import com.sparta.ditto.notification.presentation.dto.response.NotificationListResponse;
import com.sparta.ditto.notification.presentation.dto.response.ReadByRoomResponse;
import com.sparta.ditto.notification.presentation.dto.response.ReadNotificationResponse;
import com.sparta.ditto.notification.presentation.dto.response.UnreadCountResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseService sseService;

    @GetMapping("/stream")
    public SseEmitter stream(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return sseService.connect(userId);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestParam(required = false) UUID cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int size
    ) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        NotificationListResult result = notificationService.getNotifications(userId, cursor, size);
        return ResponseEntity.ok(ApiResponse.success(NotificationListResponse.from(result)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        long unreadCount = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(UnreadCountResponse.from(unreadCount)));
    }
    
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<ReadNotificationResponse>> readNotification(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID notificationId
    ) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        ReadNotificationResult result = notificationService.read(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.updated(ReadNotificationResponse.from(result)));
    }
    
    @PostMapping("/read-by-room")
    public ResponseEntity<ApiResponse<ReadByRoomResponse>> readByRoom(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestBody ReadByRoomRequest request
    ) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        if (request.roomId() == null || request.roomId().isBlank()) {
            throw new RoomIdRequiredException();
        }
        ReadByRoomResult result = notificationService.readByRoom(userId, request.roomId());
        return ResponseEntity.ok(ApiResponse.updated(ReadByRoomResponse.from(result)));
    }

}