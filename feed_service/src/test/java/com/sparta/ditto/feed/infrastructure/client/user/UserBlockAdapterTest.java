package com.sparta.ditto.feed.infrastructure.client.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.infrastructure.client.user.dto.BlockedUsersResponse;
import com.sparta.ditto.feed.infrastructure.client.user.dto.ChatUserValidationRequest;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UserBlockAdapter 단위 테스트 (UserServiceClient Feign mock 격리, Spring 컨텍스트 없음).
 *
 * <p>차단 검증은 user-service 기존 internal API {@code POST /api/v1/internal/users/chat-validation}
 * (checkBlock=true)을 재사용한다. 양방향 판정은 user-service가 하고, feed는 결과만 받는다.
 * 이 어댑터는 403 + code "BLOCK-004"만 "차단"으로 판정(true)하고, 그 외 오류는 삼키지 않고
 * 그대로 전파한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserBlockAdapterTest {

    @Mock
    private UserServiceClient userServiceClient;

    private UserBlockAdapter userBlockAdapter;

    private final UUID requesterId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        userBlockAdapter = new UserBlockAdapter(userServiceClient, new ObjectMapper());
    }

    private FeignException feignError(int status, String body) {
        Request request = Request.create(
                Request.HttpMethod.POST, "/api/v1/internal/users/chat-validation",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        Response.Builder builder = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Collections.emptyMap());
        if (body != null) {
            builder.body(body, StandardCharsets.UTF_8);
        }
        return FeignException.errorStatus("validateChatUsers", builder.build());
    }

    @Test
    @DisplayName("403 + code \"BLOCK-004\" → 차단 관계로 판정하여 true 반환")
    void isBlocked_true_on403Block004() {
        willThrow(feignError(403, "{\"status\":403,\"code\":\"BLOCK-004\",\"message\":\"차단된 사용자입니다.\"}"))
                .given(userServiceClient).validateChatUsers(any(ChatUserValidationRequest.class));

        boolean blocked = userBlockAdapter.isBlockedEitherDirection(requesterId, ownerId);

        assertThat(blocked).isTrue();
    }

    @Test
    @DisplayName("그 외 4xx(USER_NOT_FOUND 404) → 차단 오판 없이 예외를 그대로 전파")
    void propagate_onOther4xx() {
        willThrow(feignError(404, "{\"status\":404,\"code\":\"USER_NOT_FOUND\",\"message\":\"사용자를 찾을 수 없습니다.\"}"))
                .given(userServiceClient).validateChatUsers(any(ChatUserValidationRequest.class));

        assertThatThrownBy(() -> userBlockAdapter.isBlockedEitherDirection(requesterId, ownerId))
                .isInstanceOf(FeignException.class);
    }

    @Test
    @DisplayName("403이지만 code가 BLOCK-004가 아니면 차단으로 오판하지 않고 예외를 전파한다")
    void propagate_on403ButNotBlock004() {
        willThrow(feignError(403, "{\"status\":403,\"code\":\"SOME_OTHER\",\"message\":\"권한 없음\"}"))
                .given(userServiceClient).validateChatUsers(any(ChatUserValidationRequest.class));

        assertThatThrownBy(() -> userBlockAdapter.isBlockedEitherDirection(requesterId, ownerId))
                .isInstanceOf(FeignException.class);
    }

    @Test
    @DisplayName("5xx/타임아웃 → 예외를 삼키지 않고 전파 (fail-open은 Application 몫)")
    void propagate_on5xx() {
        willThrow(feignError(500, null))
                .given(userServiceClient).validateChatUsers(any(ChatUserValidationRequest.class));

        assertThatThrownBy(() -> userBlockAdapter.isBlockedEitherDirection(requesterId, ownerId))
                .isInstanceOf(FeignException.class);
    }

    @Test
    @DisplayName("차단 없음(2xx) → false 반환, checkBlock=true·targetUserIds=[작성자]로 호출")
    void returnsFalse_andSendsBlockCheckRequest() {
        boolean blocked = userBlockAdapter.isBlockedEitherDirection(requesterId, ownerId);

        assertThat(blocked).isFalse();

        ArgumentCaptor<ChatUserValidationRequest> captor =
                ArgumentCaptor.forClass(ChatUserValidationRequest.class);
        verify(userServiceClient).validateChatUsers(captor.capture());
        ChatUserValidationRequest sent = captor.getValue();
        assertThat(sent.requesterId()).isEqualTo(requesterId);
        assertThat(sent.targetUserIds()).containsExactly(ownerId);
        assertThat(sent.checkBlock()).isTrue();
    }

    // ── 피드용: 내가 차단한 사용자 ID 목록 조회 (me/blocks) ─────────────────────

    @Test
    @DisplayName("me/blocks 응답에서 data[].id만 UUID 목록으로 추출 (nickname 등 무시)")
    void findBlockedUserIds_extractsIdsOnly() {
        UUID blockedA = UUID.randomUUID();
        UUID blockedB = UUID.randomUUID();
        given(userServiceClient.getMyBlocks(requesterId))
                .willReturn(new BlockedUsersResponse(200, "SUCCESS",
                        List.of(new BlockedUsersResponse.BlockedUser(blockedA),
                                new BlockedUsersResponse.BlockedUser(blockedB))));

        List<UUID> ids = userBlockAdapter.findBlockedUserIds(requesterId);

        assertThat(ids).containsExactly(blockedA, blockedB);
    }

    @Test
    @DisplayName("data가 빈 배열이면 빈 목록 반환")
    void findBlockedUserIds_emptyData() {
        given(userServiceClient.getMyBlocks(requesterId))
                .willReturn(new BlockedUsersResponse(200, "SUCCESS", List.of()));

        assertThat(userBlockAdapter.findBlockedUserIds(requesterId)).isEmpty();
    }

    @Test
    @DisplayName("Feign 예외(타임아웃/5xx)는 삼키지 않고 전파")
    void findBlockedUserIds_propagatesFeignError() {
        given(userServiceClient.getMyBlocks(requesterId)).willThrow(feignError(500, null));

        assertThatThrownBy(() -> userBlockAdapter.findBlockedUserIds(requesterId))
                .isInstanceOf(FeignException.class);
    }

    @Test
    @DisplayName("X-User-Id로 요청자 ID가 전달된다")
    void findBlockedUserIds_sendsRequesterId() {
        given(userServiceClient.getMyBlocks(requesterId))
                .willReturn(new BlockedUsersResponse(200, "SUCCESS", List.of()));

        userBlockAdapter.findBlockedUserIds(requesterId);

        verify(userServiceClient).getMyBlocks(requesterId);
    }
}