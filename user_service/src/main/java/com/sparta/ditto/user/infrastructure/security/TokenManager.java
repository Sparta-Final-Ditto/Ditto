package com.sparta.ditto.user.infrastructure.security;

import com.sparta.ditto.user.domain.user.User;
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
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getRole(), user.getNickname());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getRole(), user.getNickname());
        refreshTokenRepository.save(user.getId(), refreshToken, jwtProperties.refreshTokenValidity());
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