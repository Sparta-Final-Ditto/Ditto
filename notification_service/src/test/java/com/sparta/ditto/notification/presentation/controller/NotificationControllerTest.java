package com.sparta.ditto.notification.presentation.controller;

import com.sparta.ditto.common.exception.GlobalExceptionHandler;
import com.sparta.ditto.notification.application.NotificationService;
import com.sparta.ditto.notification.application.dto.NotificationItemResult;
import com.sparta.ditto.notification.application.dto.NotificationListResult;
import com.sparta.ditto.notification.application.dto.ReadNotificationResult;
import com.sparta.ditto.notification.domain.exception.NotificationNotFoundException;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("빈 목록도 200 OK, message=SUCCESS, notifications=[], hasNext=false, nextCursor 미포함")
    void getNotifications_빈목록_200OK() throws Exception {
        when(notificationService.getNotifications(eq(userId), isNull(), eq(20)))
                .thenReturn(new NotificationListResult(0L, List.of(), null, false));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.notifications").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.nextCursor").doesNotExist());
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 401, code=COMMON-002")
    void getNotifications_헤더누락_401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    @DisplayName("채팅 알림은 roomUnreadCount 포함, 비채팅은 미포함, ID 필드는 문자열 직렬화")
    void getNotifications_정상응답_JSON필드_검증() throws Exception {
        UUID likeNotifId = UUID.randomUUID();
        UUID chatNotifId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.now();

        NotificationItemResult likeItem = new NotificationItemResult(
                likeNotifId, NotificationType.LIKE, actorId, TargetType.LIKE,
                "post-123", "A님이 좋아요를 눌렀습니다.", false,
                "{\"postId\":\"post-123\"}", now, null
        );
        NotificationItemResult chatItem = new NotificationItemResult(
                chatNotifId, NotificationType.CHAT_MESSAGE, actorId, TargetType.CHAT_MESSAGE,
                "msg-456", "안녕하세요", false,
                "{\"roomId\":\"room1\",\"senderNickname\":\"A\",\"senderProfileImageUrl\":null}",
                now, 3L
        );

        when(notificationService.getNotifications(eq(userId), isNull(), eq(20)))
                .thenReturn(new NotificationListResult(
                        2L, List.of(likeItem, chatItem), null, false));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2))
                // LIKE: roomUnreadCount 키 자체 미포함
                .andExpect(jsonPath("$.data.notifications[0].notificationId")
                        .value(likeNotifId.toString()))
                .andExpect(jsonPath("$.data.notifications[0].actorId")
                        .value(actorId.toString()))
                .andExpect(jsonPath("$.data.notifications[0].targetId").value("post-123"))
                .andExpect(jsonPath("$.data.notifications[0].roomUnreadCount").doesNotExist())
                // CHAT_MESSAGE: roomUnreadCount 포함
                .andExpect(jsonPath("$.data.notifications[1].notificationId")
                        .value(chatNotifId.toString()))
                .andExpect(jsonPath("$.data.notifications[1].roomUnreadCount").value(3));
    }

    // ── GET /notifications/unread-count ──────────────────────────────────────

    @Test
    @DisplayName("미읽음 수 조회 200 OK, message=SUCCESS, unreadCount 반환")
    void getUnreadCount_200OK() throws Exception {
        // given
        when(notificationService.getUnreadCount(userId)).thenReturn(3L);

        // when / then
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("SUCCESS"))
                .andExpect(jsonPath("$.data.unreadCount").value(3));
    }

    @Test
    @DisplayName("X-User-Id 헤더 누락 시 401, code=COMMON-002")
    void getUnreadCount_헤더누락_401() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/unread-count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    // ── PATCH /notifications/{notificationId}/read ────────────────────────────

    // 존재하지 않는 알림 / 타인 소유 알림 → 동일하게 404
    @ParameterizedTest(name = "{0}")
    @MethodSource("notFoundScenarios")
    @DisplayName("읽음 처리 - 알림 없음/타인 소유 → 404 NOTIFICATION_NOT_FOUND")
    void readNotification_notFound_404(String label) throws Exception {
        UUID notificationId = UUID.randomUUID();
        when(notificationService.read(eq(userId), any(UUID.class)))
                .thenThrow(new NotificationNotFoundException());

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    static Stream<Arguments> notFoundScenarios() {
        return Stream.of(
                Arguments.of("존재하지 않는 알림"),
                Arguments.of("타인 소유 알림")
        );
    }

    @Test
    @DisplayName("읽음 처리 성공 - 200 UPDATED, notificationId 문자열, isRead=true")
    void readNotification_성공_200UPDATED() throws Exception {
        UUID notificationId = UUID.randomUUID();
        when(notificationService.read(userId, notificationId))
                .thenReturn(new ReadNotificationResult(notificationId, true));

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId)
                        .header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("UPDATED"))
                .andExpect(jsonPath("$.data.notificationId").value(notificationId.toString()))
                .andExpect(jsonPath("$.data.isRead").value(true));
    }

    @Test
    @DisplayName("읽음 처리 - X-User-Id 헤더 누락 시 401 COMMON-002")
    void readNotification_헤더누락_401() throws Exception {
        UUID notificationId = UUID.randomUUID();

        mockMvc.perform(patch("/api/v1/notifications/{notificationId}/read", notificationId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("COMMON-002"));
    }
}