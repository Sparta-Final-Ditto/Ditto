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
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * UserBlockAdapter 단위 테스트 (UserServiceClient Feign mock 격리, Spring 컨텍스트 없음).
 *
 * <p>차단 검증은 user-service 기존 internal API {@code chat-validation}(checkBlock=true)을,
 * 피드 차단 목록은 {@code me/blocks}를 재사용한다. 이 어댑터는 403 + code "BLOCK-004"만
 * "차단"으로 판정(true)하고, 그 외 오류는 삼키지 않고 그대로 전파한다.</p>
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
        return FeignException.errorStatus("userService", builder.build());
    }

    // ── 007-4: BLOCK-004 → 차단 판정, 2xx → 통과 + 요청 계약 ─────────────────────

    @Test
    @DisplayName("007-4: 403 + code \"BLOCK-004\" → 차단 관계로 판정(true)")
    void isBlocked_true_on403Block004() {
        willThrow(feignError(403, "{\"code\":\"BLOCK-004\"}"))
                .given(userServiceClient).validateChatUsers(any(ChatUserValidationRequest.class));

        assertThat(userBlockAdapter.isBlockedEitherDirection(requesterId, ownerId)).isTrue();
    }

    @Test
    @DisplayName("007-4: 차단 없음(2xx) → false, checkBlock=true·targetUserIds=[작성자]로 호출")
    void isBlocked_false_andSendsBlockCheckRequest() {
        assertThat(userBlockAdapter.isBlockedEitherDirection(requesterId, ownerId)).isFalse();

        ArgumentCaptor<ChatUserValidationRequest> captor =
                ArgumentCaptor.forClass(ChatUserValidationRequest.class);
        verify(userServiceClient).validateChatUsers(captor.capture());
        ChatUserValidationRequest sent = captor.getValue();
        assertThat(sent.requesterId()).isEqualTo(requesterId);
        assertThat(sent.targetUserIds()).containsExactly(ownerId);
        assertThat(sent.checkBlock()).isTrue();
    }

    // ── 예외 전파: validate/list × 404·5xx·403(BLOCK-004 아님) 모두 삼키지 않고 전파 ──

    private enum Op { VALIDATE, LIST }

    static Stream<Arguments> propagationCases() {
        return Stream.of(
                Arguments.of("007-4 validate 404(USER_NOT_FOUND) 전파", Op.VALIDATE, 404,
                        "{\"code\":\"USER_NOT_FOUND\"}"),
                Arguments.of("007-4 validate 403(BLOCK-004 아님) 전파", Op.VALIDATE, 403,
                        "{\"code\":\"SOME_OTHER\"}"),
                Arguments.of("007-9 validate 5xx 전파", Op.VALIDATE, 500, null),
                Arguments.of("004-8 list 404 전파", Op.LIST, 404, "{\"code\":\"USER_NOT_FOUND\"}"),
                Arguments.of("004-8 list 5xx 전파", Op.LIST, 500, null)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("propagationCases")
    @DisplayName("차단 오판 없이 FeignException을 그대로 전파 (fail-open은 Application 몫)")
    void propagatesFeignError(String desc, Op op, int status, String body) {
        FeignException error = feignError(status, body);
        if (op == Op.VALIDATE) {
            willThrow(error).given(userServiceClient)
                    .validateChatUsers(any(ChatUserValidationRequest.class));
            assertThatThrownBy(
                    () -> userBlockAdapter.isBlockedEitherDirection(requesterId, ownerId))
                    .isInstanceOf(FeignException.class);
        } else {
            given(userServiceClient.getMyBlocks(requesterId)).willThrow(error);
            assertThatThrownBy(() -> userBlockAdapter.findBlockedUserIds(requesterId))
                    .isInstanceOf(FeignException.class);
        }
    }

    // ── 004-8/005-6/003-14/004-13: me/blocks 목록 조회 ─────────────────────────

    @Test
    @DisplayName("004-8/005-6: me/blocks 응답에서 data[].id만 UUID 목록으로 추출")
    void findBlockedUserIds_extractsIdsOnly() {
        UUID blockedA = UUID.randomUUID();
        UUID blockedB = UUID.randomUUID();
        given(userServiceClient.getMyBlocks(requesterId))
                .willReturn(new BlockedUsersResponse(200, "SUCCESS",
                        List.of(new BlockedUsersResponse.BlockedUser(blockedA),
                                new BlockedUsersResponse.BlockedUser(blockedB))));

        assertThat(userBlockAdapter.findBlockedUserIds(requesterId))
                .containsExactly(blockedA, blockedB);
    }

    @Test
    @DisplayName("003-14/004-13: data가 빈 배열이면 빈 목록 반환")
    void findBlockedUserIds_emptyData() {
        given(userServiceClient.getMyBlocks(requesterId))
                .willReturn(new BlockedUsersResponse(200, "SUCCESS", List.of()));

        assertThat(userBlockAdapter.findBlockedUserIds(requesterId)).isEmpty();
    }

    @Test
    @DisplayName("004-8: X-User-Id로 요청자 ID가 전달된다")
    void findBlockedUserIds_sendsRequesterId() {
        given(userServiceClient.getMyBlocks(requesterId))
                .willReturn(new BlockedUsersResponse(200, "SUCCESS", List.of()));

        userBlockAdapter.findBlockedUserIds(requesterId);

        verify(userServiceClient).getMyBlocks(requesterId);
    }
}
