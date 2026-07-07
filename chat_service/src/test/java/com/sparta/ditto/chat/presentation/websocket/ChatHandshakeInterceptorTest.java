package com.sparta.ditto.chat.presentation.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

@DisplayName("ChatHandshakeInterceptor 테스트")
class ChatHandshakeInterceptorTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final ChatHandshakeInterceptor interceptor = new ChatHandshakeInterceptor();
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final WebSocketHandler wsHandler = mock(WebSocketHandler.class);

    @Test
    @DisplayName("유효한 X-User-Id면 연결을 허용하고 세션에 userId를 저장한다")
    void valid_user_allows_handshake() {
        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                requestWith(USER_ID.toString()), response, wsHandler, attributes);
        assertThat(result).isTrue();
        assertThat(attributes.get(ChatHandshakeInterceptor.USER_ID_ATTRIBUTE)).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("X-User-Id가 없으면 연결을 거부한다")
    void missing_user_rejects_handshake() {
        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                requestWith(null), response, wsHandler, attributes);
        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("X-User-Id가 UUID 형식이 아니면 연결을 거부한다")
    void invalid_user_rejects_handshake() {
        Map<String, Object> attributes = new HashMap<>();
        boolean result = interceptor.beforeHandshake(
                requestWith("not-a-uuid"), response, wsHandler, attributes);
        assertThat(result).isFalse();
        assertThat(attributes).isEmpty();
    }

    private ServerHttpRequest requestWith(String userId) {
        HttpHeaders headers = new HttpHeaders();
        if (userId != null) {
            headers.add("X-User-Id", userId);
        }
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        given(request.getHeaders()).willReturn(headers);
        return request;
    }
}
