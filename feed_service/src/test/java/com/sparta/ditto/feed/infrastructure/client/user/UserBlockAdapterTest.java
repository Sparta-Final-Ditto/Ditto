package com.sparta.ditto.feed.infrastructure.client.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.feed.infrastructure.client.user.dto.BlockRelationsResponse;
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
 * 피드 차단 관계 목록은 internal API {@code block-relations}(양방향)를 사용한다. chat-validation은
 * 403 + code "BLOCK-004"만 "차단"으로 판정(true)하고, block-relations는 응답의
 * {@code blockedUserIds}(내가 차단) ∪ {@code blockedByUserIds}(나를 차단)를 union(중복 제거)한다.
 * 어느 경우든 오류는 삼키지 않고 그대로 전파한다.</p>
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

    private BlockRelationsResponse blockRelations(List<UUID> blocked, List<UUID> blockedBy) {
        return new BlockRelationsResponse(200, "SUCCESS",
                new BlockRelationsResponse.Data(blocked, blockedBy));
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

    // ── 예외 전파: validate/relations × 404·5xx·403(BLOCK-004 아님) 모두 삼키지 않고 전파 ──

    private enum Op { VALIDATE, RELATIONS }

    static Stream<Arguments> propagationCases() {
        return Stream.of(
                Arguments.of("007-4 validate 404(USER_NOT_FOUND) 전파", Op.VALIDATE, 404,
                        "{\"code\":\"USER_NOT_FOUND\"}"),
                Arguments.of("007-4 validate 403(BLOCK-004 아님) 전파", Op.VALIDATE, 403,
                        "{\"code\":\"SOME_OTHER\"}"),
                Arguments.of("007-9 validate 5xx 전파", Op.VALIDATE, 500, null),
                Arguments.of("004-8 block-relations 404 전파", Op.RELATIONS, 404,
                        "{\"code\":\"USER_NOT_FOUND\"}"),
                Arguments.of("004-8 block-relations 5xx 전파", Op.RELATIONS, 500, null)
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
            given(userServiceClient.getBlockRelations(requesterId)).willThrow(error);
            assertThatThrownBy(() -> userBlockAdapter.findBlockRelationUserIds(requesterId))
                    .isInstanceOf(FeignException.class);
        }
    }

    // ── 003-6/004-8/005-6: block-relations 양방향 union ─────────────────────────

    static Stream<Arguments> directionCases() {
        UUID target = UUID.randomUUID();
        return Stream.of(
                Arguments.of("내가 차단(blockedUserIds)만 존재", List.of(target), List.of(), target),
                Arguments.of("나를 차단(blockedByUserIds)만 존재", List.of(), List.of(target), target)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("directionCases")
    @DisplayName("003-6/004-8/005-6: 어느 방향이든 차단 관계 ID가 결과에 포함된다")
    void findBlockRelationUserIds_includesEitherDirection(
            String desc, List<UUID> blocked, List<UUID> blockedBy, UUID target) {
        given(userServiceClient.getBlockRelations(requesterId))
                .willReturn(blockRelations(blocked, blockedBy));

        assertThat(userBlockAdapter.findBlockRelationUserIds(requesterId)).contains(target);
    }

    @Test
    @DisplayName("맞차단: 두 목록에 동일 ID → 중복 제거된 union")
    void findBlockRelationUserIds_dedupesUnion() {
        UUID mutual = UUID.randomUUID();
        given(userServiceClient.getBlockRelations(requesterId))
                .willReturn(blockRelations(List.of(mutual), List.of(mutual)));

        assertThat(userBlockAdapter.findBlockRelationUserIds(requesterId))
                .containsExactly(mutual);
    }

    @Test
    @DisplayName("003-6/004-8/005-6: 양방향 서로 다른 ID → 합집합으로 모두 포함")
    void findBlockRelationUserIds_unionOfBothLists() {
        UUID iBlocked = UUID.randomUUID();
        UUID blockedMe = UUID.randomUUID();
        given(userServiceClient.getBlockRelations(requesterId))
                .willReturn(blockRelations(List.of(iBlocked), List.of(blockedMe)));

        assertThat(userBlockAdapter.findBlockRelationUserIds(requesterId))
                .containsExactlyInAnyOrder(iBlocked, blockedMe);
    }

    @Test
    @DisplayName("003-14/004-13: data가 null이면 빈 목록 반환")
    void findBlockRelationUserIds_nullData() {
        given(userServiceClient.getBlockRelations(requesterId))
                .willReturn(new BlockRelationsResponse(200, "SUCCESS", null));

        assertThat(userBlockAdapter.findBlockRelationUserIds(requesterId)).isEmpty();
    }

    @Test
    @DisplayName("003-14/004-13: 두 목록 모두 빈 배열이면 빈 목록 반환")
    void findBlockRelationUserIds_emptyLists() {
        given(userServiceClient.getBlockRelations(requesterId))
                .willReturn(blockRelations(List.of(), List.of()));

        assertThat(userBlockAdapter.findBlockRelationUserIds(requesterId)).isEmpty();
    }

    @Test
    @DisplayName("004-8: block-relations 조회 시 요청자 ID(userId)가 전달된다")
    void findBlockRelationUserIds_sendsRequesterId() {
        given(userServiceClient.getBlockRelations(requesterId))
                .willReturn(blockRelations(List.of(), List.of()));

        userBlockAdapter.findBlockRelationUserIds(requesterId);

        verify(userServiceClient).getBlockRelations(requesterId);
    }

    // ── JSON 계약 고정(API_SPEC 2.16 예시): 역직렬화로 두 목록 파싱 검증 ────────────

    @Test
    @DisplayName("JSON 계약: API_SPEC 2.16 예시 JSON이 두 목록으로 역직렬화된다")
    void blockRelationsResponse_deserializesBothLists() throws Exception {
        String json = """
                {
                  "status": 200,
                  "message": "SUCCESS",
                  "data": {
                    "blockedUserIds": ["7b9f6e22-03e7-4b59-a9a4-95de4e2f1234"],
                    "blockedByUserIds": ["1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678"]
                  }
                }
                """;

        BlockRelationsResponse response =
                new ObjectMapper().readValue(json, BlockRelationsResponse.class);

        assertThat(response.data().blockedUserIds())
                .containsExactly(UUID.fromString("7b9f6e22-03e7-4b59-a9a4-95de4e2f1234"));
        assertThat(response.data().blockedByUserIds())
                .containsExactly(UUID.fromString("1caa0424-5b1e-4f8a-90b5-bc1d6e2a5678"));
    }
}