package com.sparta.ditto.user.infrastructure.security;

import com.sparta.ditto.user.domain.user.enums.UserRole;
import com.sparta.ditto.user.infrastructure.security.exception.ExpiredTokenException;
import com.sparta.ditto.user.infrastructure.security.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_NICKNAME = "nickname";

    private final SecretKey secretKey;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtUtil(JwtProperties properties) {
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = properties.accessTokenValidity().toMillis();
        this.refreshTokenValidityMs = properties.refreshTokenValidity().toMillis();
    }

    public String generateAccessToken(UUID userId, UserRole role, String nickname) {
        return buildToken(userId, role, nickname, accessTokenValidityMs);
    }

    public String generateRefreshToken(UUID userId, UserRole role, String nickname) {
        return buildToken(userId, role, nickname, refreshTokenValidityMs);
    }

    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new ExpiredTokenException();
        } catch (Exception e) {
            throw new InvalidTokenException();
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    public UserRole extractRole(Claims claims) {
        return UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
    }

    private String buildToken(UUID userId, UserRole role, String nickname, long validityMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_NICKNAME, nickname)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validityMs))
                .signWith(secretKey)
                .compact();
    }
}
