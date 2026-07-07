package com.sparta.ditto.user.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.domain.user.enums.UserRole;
import com.sparta.ditto.user.infrastructure.repository.RefreshTokenRepository;
import com.sparta.ditto.user.infrastructure.security.exception.InvalidTokenException;
import com.sparta.ditto.user.presentation.dto.response.AuthTokenResponse;
import io.jsonwebtoken.Claims;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TokenManagerTest {

    @InjectMocks
    private TokenManager tokenManager;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.createEmailUser(
                "test@test.com", "encodedPassword", "testNick", Gender.MALE, LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(user, "id", userId);
    }

    @Nested
    class IssueTokens {

        @Test
        void 성공() {
            Duration ttl = Duration.ofDays(7);
            given(jwtUtil.generateAccessToken(userId, UserRole.USER, "testNick")).willReturn("accessToken");
            given(jwtUtil.generateRefreshToken(userId, UserRole.USER, "testNick")).willReturn("refreshToken");
            given(jwtProperties.refreshTokenValidity()).willReturn(ttl);

            AuthTokenResponse result = tokenManager.issueTokens(user);

            assertThat(result.accessToken()).isEqualTo("accessToken");
            assertThat(result.refreshToken()).isEqualTo("refreshToken");
            then(refreshTokenRepository).should().save(userId, "refreshToken", ttl);
        }
    }

    @Nested
    class ValidateRefreshToken {

        @Test
        void 성공() {
            String refreshToken = "validRefreshToken";
            Claims claims = mock(Claims.class);
            given(jwtUtil.parseToken(refreshToken)).willReturn(claims);
            given(jwtUtil.extractUserId(claims)).willReturn(userId);
            given(refreshTokenRepository.find(userId)).willReturn(Optional.of(refreshToken));

            UUID result = tokenManager.validateRefreshToken(refreshToken);

            assertThat(result).isEqualTo(userId);
        }

        @Test
        void Redis에_없음_예외() {
            String refreshToken = "validRefreshToken";
            Claims claims = mock(Claims.class);
            given(jwtUtil.parseToken(refreshToken)).willReturn(claims);
            given(jwtUtil.extractUserId(claims)).willReturn(userId);
            given(refreshTokenRepository.find(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> tokenManager.validateRefreshToken(refreshToken))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void 토큰_불일치_예외() {
            String refreshToken = "validRefreshToken";
            Claims claims = mock(Claims.class);
            given(jwtUtil.parseToken(refreshToken)).willReturn(claims);
            given(jwtUtil.extractUserId(claims)).willReturn(userId);
            given(refreshTokenRepository.find(userId)).willReturn(Optional.of("differentToken"));

            assertThatThrownBy(() -> tokenManager.validateRefreshToken(refreshToken))
                    .isInstanceOf(InvalidTokenException.class);
        }
    }

    @Nested
    class DeleteToken {

        @Test
        void 성공() {
            tokenManager.deleteToken(userId);

            then(refreshTokenRepository).should().delete(userId);
        }
    }
}