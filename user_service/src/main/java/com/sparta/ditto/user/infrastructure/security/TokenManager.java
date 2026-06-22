package com.sparta.ditto.user.infrastructure.security;

import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.enums.UserRole;
import com.sparta.ditto.user.infrastructure.repository.RefreshTokenRepository;
import com.sparta.ditto.user.infrastructure.security.exception.InvalidTokenException;
import com.sparta.ditto.user.presentation.dto.response.AuthTokenResponse;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenManager {

    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthTokenResponse issueTokens(User user) {
        UUID userId = user.getId();
        UserRole role = user.getRole();
        String nickname = user.getNickname();
        String accessToken = jwtUtil.generateAccessToken(userId, role, nickname);
        String refreshToken = jwtUtil.generateRefreshToken(userId, role, nickname);
        refreshTokenRepository.save(userId, refreshToken, jwtProperties.refreshTokenValidity());
        return new AuthTokenResponse(accessToken, refreshToken);
    }

    public UUID validateRefreshToken(String refreshToken) {
        Claims claims = jwtUtil.parseToken(refreshToken);
        UUID userId = jwtUtil.extractUserId(claims);

        String stored = refreshTokenRepository.find(userId)
                .orElseThrow(InvalidTokenException::new);

        if (!stored.equals(refreshToken)) {
            throw new InvalidTokenException();
        }

        return userId;
    }

    public void deleteToken(UUID userId) {
        refreshTokenRepository.delete(userId);
    }
}
