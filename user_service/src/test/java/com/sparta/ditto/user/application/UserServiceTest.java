package com.sparta.ditto.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.domain.user.exception.NicknameAlreadyExistsException;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.kafka.UserEventProducer;
import com.sparta.ditto.user.infrastructure.repository.UserRepository;
import com.sparta.ditto.user.infrastructure.security.TokenManager;
import com.sparta.ditto.user.presentation.dto.request.UserUpdateRequest;
import com.sparta.ditto.user.presentation.dto.response.AuthTokenResponse;
import com.sparta.ditto.user.presentation.dto.response.UserUpdateResponse;
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
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenManager tokenManager;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserEventProducer userEventProducer;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.createEmailUser(
                "test@test.com", "encodedPassword", "testNick", Gender.MALE, "19900101");
        ReflectionTestUtils.setField(user, "id", userId);
    }

    @Nested
    class UpdateProfile {

        @Test
        void 성공_닉네임_변경_토큰_재발급() {
            UserUpdateRequest request = new UserUpdateRequest("newNick", "bio", null);
            AuthTokenResponse tokenResponse = new AuthTokenResponse("accessToken", "refreshToken");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userRepository.existsByNickname("newNick")).willReturn(false);
            given(tokenManager.issueTokens(user)).willReturn(tokenResponse);

            UserUpdateResponse result = userService.updateProfile(userId, request);

            assertThat(result.nickname()).isEqualTo("newNick");
            assertThat(result.tokens()).isEqualTo(tokenResponse);
        }

        @Test
        void 성공_닉네임_변경_없이_토큰_재발급_없음() {
            UserUpdateRequest request = new UserUpdateRequest(null, "새로운 소개", "http://img.url");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            UserUpdateResponse result = userService.updateProfile(userId, request);

            assertThat(result.bio()).isEqualTo("새로운 소개");
            assertThat(result.tokens()).isNull();
            then(tokenManager).shouldHaveNoInteractions();
        }

        @Test
        void 유저_없음_예외() {
            UserUpdateRequest request = new UserUpdateRequest("newNick", null, null);
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateProfile(userId, request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void 닉네임_중복_예외() {
            UserUpdateRequest request = new UserUpdateRequest("dupNick", null, null);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(userRepository.existsByNickname("dupNick")).willReturn(true);

            assertThatThrownBy(() -> userService.updateProfile(userId, request))
                    .isInstanceOf(NicknameAlreadyExistsException.class);
        }

        @Test
        void 동일_닉네임_변경_없이_토큰_재발급_없음() {
            UserUpdateRequest request = new UserUpdateRequest("testNick", null, null);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            UserUpdateResponse result = userService.updateProfile(userId, request);

            assertThat(result.tokens()).isNull();
            then(tokenManager).shouldHaveNoInteractions();
        }
    }
}