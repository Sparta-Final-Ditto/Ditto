package com.sparta.ditto.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

import com.sparta.ditto.user.application.port.NeighborhoodPort;
import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.domain.user.enums.UserStatus;
import com.sparta.ditto.user.domain.user.exception.EmailAlreadyExistsException;
import com.sparta.ditto.user.domain.user.exception.InvalidPasswordException;
import com.sparta.ditto.user.domain.user.exception.NicknameAlreadyExistsException;
import com.sparta.ditto.user.domain.user.exception.UserBannedException;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.kafka.UserCreatedEvent;
import com.sparta.ditto.user.infrastructure.kafka.UserEventProducer;
import com.sparta.ditto.user.infrastructure.repository.UserRepository;
import com.sparta.ditto.user.infrastructure.security.TokenManager;
import com.sparta.ditto.user.infrastructure.security.exception.InvalidTokenException;
import com.sparta.ditto.user.presentation.dto.request.AuthLoginRequest;
import com.sparta.ditto.user.presentation.dto.request.AuthReissueRequest;
import com.sparta.ditto.user.presentation.dto.request.AuthSignupRequest;
import com.sparta.ditto.user.presentation.dto.response.AuthTokenResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserEventProducer userEventProducer;

    @Mock
    private NeighborhoodPort neighborhoodPort;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.createEmailUser("test@test.com", "encodedPassword", "testNick", Gender.MALE, LocalDate.of(1990, 1, 1));
        ReflectionTestUtils.setField(user, "id", userId);
    }

    @Nested
    class Signup {

        @Test
        void 성공() {
            AuthSignupRequest request = new AuthSignupRequest(
                    "test@test.com", "password123", "testNick", Gender.MALE,
                    LocalDate.of(1990, 1, 1), 37.5563, 127.0374);
            given(userRepository.existsByEmail(request.email())).willReturn(false);
            given(userRepository.existsByNickname(request.nickname())).willReturn(false);
            given(passwordEncoder.encode(request.password())).willReturn("encodedPassword");
            given(neighborhoodPort.resolveNeighborhood(request.latitude(), request.longitude()))
                    .willReturn("서울 성동구");

            authService.signup(request);

            then(userRepository).should().save(any(User.class));
            then(userEventProducer).should().sendUserCreated(any(UserCreatedEvent.class));
        }

        @Test
        void 이메일_중복_예외() {
            AuthSignupRequest request = new AuthSignupRequest(
                    "test@test.com", "password123", "testNick", Gender.MALE,
                    LocalDate.of(1990, 1, 1), 37.5563, 127.0374);
            given(userRepository.existsByEmail(request.email())).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(EmailAlreadyExistsException.class);
            then(userRepository).should(never()).save(any());
        }

        @Test
        void 닉네임_중복_예외() {
            AuthSignupRequest request = new AuthSignupRequest(
                    "test@test.com", "password123", "testNick", Gender.MALE,
                    LocalDate.of(1990, 1, 1), 37.5563, 127.0374);
            given(userRepository.existsByEmail(request.email())).willReturn(false);
            given(userRepository.existsByNickname(request.nickname())).willReturn(true);

            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(NicknameAlreadyExistsException.class);
            then(userRepository).should(never()).save(any());
        }
    }

    @Nested
    class Login {

        @Test
        void 성공() {
            AuthLoginRequest request = new AuthLoginRequest("test@test.com", "password123");
            AuthTokenResponse tokenResponse = new AuthTokenResponse("accessToken", "refreshToken");
            given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);
            given(tokenManager.issueTokens(user)).willReturn(tokenResponse);

            AuthTokenResponse result = authService.login(request);

            assertThat(result).isEqualTo(tokenResponse);
        }

        @Test
        void 유저_없음_예외() {
            AuthLoginRequest request = new AuthLoginRequest("test@test.com", "password123");
            given(userRepository.findByEmail(request.email())).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void 정지_유저_예외() {
            AuthLoginRequest request = new AuthLoginRequest("test@test.com", "password123");
            ReflectionTestUtils.setField(user, "status", UserStatus.BANNED);
            given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(UserBannedException.class);
        }

        @Test
        void 비밀번호_불일치_예외() {
            AuthLoginRequest request = new AuthLoginRequest("test@test.com", "wrongPassword");
            given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
            given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(false);

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(InvalidPasswordException.class);
        }
    }

    @Nested
    class Logout {

        @Test
        void 성공() {
            authService.logout(userId);

            then(tokenManager).should().deleteToken(userId);
        }
    }

    @Nested
    class Reissue {

        @Test
        void 성공() {
            AuthReissueRequest request = new AuthReissueRequest("refreshToken");
            AuthTokenResponse tokenResponse = new AuthTokenResponse("newAccessToken", "newRefreshToken");
            given(tokenManager.validateRefreshToken(request.refreshToken())).willReturn(userId);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(tokenManager.issueTokens(user)).willReturn(tokenResponse);

            AuthTokenResponse result = authService.reissue(request);

            assertThat(result).isEqualTo(tokenResponse);
        }

        @Test
        void 토큰_검증_실패_예외() {
            AuthReissueRequest request = new AuthReissueRequest("invalidToken");
            given(tokenManager.validateRefreshToken(request.refreshToken()))
                    .willThrow(new InvalidTokenException());

            assertThatThrownBy(() -> authService.reissue(request))
                    .isInstanceOf(InvalidTokenException.class);
        }

        @Test
        void 유저_없음_예외() {
            AuthReissueRequest request = new AuthReissueRequest("refreshToken");
            given(tokenManager.validateRefreshToken(request.refreshToken())).willReturn(userId);
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> authService.reissue(request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void 정지_유저_예외() {
            AuthReissueRequest request = new AuthReissueRequest("refreshToken");
            ReflectionTestUtils.setField(user, "status", UserStatus.BANNED);
            given(tokenManager.validateRefreshToken(request.refreshToken())).willReturn(userId);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.reissue(request))
                    .isInstanceOf(UserBannedException.class);
        }
    }
}