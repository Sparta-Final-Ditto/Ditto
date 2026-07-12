package com.sparta.ditto.gateway.filter;

import com.sparta.ditto.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    private static final List<String> WHITELIST = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/reissue",
            "/api/v1/assistant",
            "/actuator"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        String token;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else if (path.startsWith("/ws-chat")) {
            // 브라우저 WebSocket은 Authorization 헤더를 못 실으므로 token 쿼리 파라미터를 허용한다.
            token = exchange.getRequest().getQueryParams().getFirst("token");
        } else {
            token = null;
        }

        if (token == null || !jwtUtil.isValid(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        Claims claims = jwtUtil.parseClaims(token);
        String nickname = claims.get(JwtUtil.CLAIM_NICKNAME, String.class);

        ServerWebExchange modifiedExchange = exchange.mutate()
                .request(r -> {
                    r.header("X-User-Id", claims.getSubject());
                    r.header("X-User-Role", claims.get(JwtUtil.CLAIM_ROLE, String.class));
                    // HTTP 헤더는 기본적으로 ISO-8859-1로 처리되어 한글 등 non-ASCII 닉네임이
                    // 깨지므로, URL 인코딩해서 전달하고 수신 측(feed_service)에서 디코딩한다.
                    if (nickname != null) {
                        r.header("X-User-Nickname", URLEncoder.encode(nickname, StandardCharsets.UTF_8));
                    }
                })
                .build();

        return chain.filter(modifiedExchange);
    }

    @Override
    public int getOrder() {
        return -1;
    }

    private boolean isWhitelisted(String path) {
        return WHITELIST.stream().anyMatch(path::startsWith);
    }
}
