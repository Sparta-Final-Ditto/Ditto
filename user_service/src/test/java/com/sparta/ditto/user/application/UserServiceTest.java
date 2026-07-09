package com.sparta.ditto.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.sparta.ditto.user.application.port.NeighborhoodPort;
import com.sparta.ditto.user.domain.block.exception.BlockedUserException;
import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.domain.user.exception.InvalidPasswordException;
import com.sparta.ditto.user.domain.user.exception.NicknameAlreadyExistsException;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.kafka.UserEventProducer;
import com.sparta.ditto.user.infrastructure.kafka.UserInterestsRegisteredEvent;
import com.sparta.ditto.user.infrastructure.repository.BlockRepository;
import com.sparta.ditto.user.infrastructure.repository.UserRepository;
import com.sparta.ditto.user.infrastructure.security.TokenManager;
import com.sparta.ditto.user.presentation.dto.request.UserInterestRequest;
import com.sparta.ditto.user.presentation.dto.request.UserLocationUpdateRequest;
import com.sparta.ditto.user.presentation.dto.request.UserPasswordChangeRequest;
import com.sparta.ditto.user.presentation.dto.request.UserUpdateRequest;
import com.sparta.ditto.user.presentation.dto.response.AuthTokenResponse;
import com.sparta.ditto.user.presentation.dto.response.UserLocationUpdateResponse;
import com.sparta.ditto.user.presentation.dto.response.UserProfileResponse;
import com.sparta.ditto.user.presentation.dto.response.UserPublicProfileResponse;
import com.sparta.ditto.user.presentation.dto.response.UserUpdateResponse;
import java.time.LocalDate;
import java.util.List;
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
    private BlockRepository blockRepository;

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
        user = User.createEmailUser(
                "test@test.com",
                "encodedPassword",
                "testNick",
                Gender.MALE,
                LocalDate.of(1990, 1, 1)
        );
        ReflectionTestUtils.setField(user, "id", userId);
    }

    @Nested
    class GetProfile {

        @Test
        void 성공() {
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            UserProfileResponse result = userService.getProfile(userId);

            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.email()).isEqualTo("test@test.com");
            assertThat(result.nickname()).isEqualTo("testNick");
        }

        @Test
        void 유저_없음_예외() {
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getProfile(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class GetPublicProfile {

        @Test
        void 성공() {
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            UserPublicProfileResponse result = userService.getPublicProfile(userId);

            assertThat(result.id()).isEqualTo(userId);
            assertThat(result.nickname()).isEqualTo("testNick");
        }

        @Test
        void 유저_없음_예외() {
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getPublicProfile(userId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class ValidateChatUsers {

        @Test
        void 성공() {
            UUID targetUserId = UUID.randomUUID();
            given(userRepository.countByIdIn(any())).willReturn(2L);
            given(blockRepository.existsByBlockerIdAndBlockedId(userId, targetUserId))
                    .willReturn(false);
            given(blockRepository.existsByBlockerIdAndBlockedId(targetUserId, userId))
                    .willReturn(false);

            userService.validateChatUsers(userId, List.of(targetUserId), true);

            then(blockRepository).should()
                    .existsByBlockerIdAndBlockedId(userId, targetUserId);
            then(blockRepository).should()
                    .existsByBlockerIdAndBlockedId(targetUserId, userId);
        }

        @Test
        void 요청자_없음_예외() {
            UUID targetUserId = UUID.randomUUID();
            given(userRepository.countByIdIn(any())).willReturn(1L);

            assertThatThrownBy(() ->
                    userService.validateChatUsers(userId, List.of(targetUserId), true))
                    .isInstanceOf(UserNotFoundException.class);

            then(blockRepository).shouldHaveNoInteractions();
        }

        @Test
        void 대상_없음_예외() {
            UUID targetUserId = UUID.randomUUID();
            given(userRepository.countByIdIn(any())).willReturn(1L);

            assertThatThrownBy(() ->
                    userService.validateChatUsers(userId, List.of(targetUserId), true))
                    .isInstanceOf(UserNotFoundException.class);

            then(blockRepository).shouldHaveNoInteractions();
        }

        @Test
        void 요청자가_대상을_차단했으면_예외() {
            UUID targetUserId = UUID.randomUUID();
            given(userRepository.countByIdIn(any())).willReturn(2L);
            given(blockRepository.existsByBlockerIdAndBlockedId(userId, targetUserId))
                    .willReturn(true);

            assertThatThrownBy(() ->
                    userService.validateChatUsers(userId, List.of(targetUserId), true))
                    .isInstanceOf(BlockedUserException.class);

            then(blockRepository).should(never())
                    .existsByBlockerIdAndBlockedId(targetUserId, userId);
        }

        @Test
        void 대상이_요청자를_차단했으면_예외() {
            UUID targetUserId = UUID.randomUUID();
            given(userRepository.countByIdIn(any())).willReturn(2L);
            given(blockRepository.existsByBlockerIdAndBlockedId(userId, targetUserId))
                    .willReturn(false);
            given(blockRepository.existsByBlockerIdAndBlockedId(targetUserId, userId))
                    .willReturn(true);

            assertThatThrownBy(() ->
                    userService.validateChatUsers(userId, List.of(targetUserId), true))
                    .isInstanceOf(BlockedUserException.class);
        }
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

    @Nested
    class UpdateLocation {

        @Test
        void 성공() {
            UserLocationUpdateRequest request = new UserLocationUpdateRequest(37.5563, 127.0374);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(neighborhoodPort.resolveNeighborhood(37.5563, 127.0374)).willReturn("서울 성동구");

            UserLocationUpdateResponse result = userService.updateLocation(userId, request);

            assertThat(result.latitude()).isEqualTo(37.5563);
            assertThat(result.longitude()).isEqualTo(127.0374);
            assertThat(result.neighborhood()).isEqualTo("서울 성동구");
        }

        @Test
        void 동네명_조회_실패해도_좌표는_저장() {
            UserLocationUpdateRequest request = new UserLocationUpdateRequest(37.5563, 127.0374);
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(neighborhoodPort.resolveNeighborhood(37.5563, 127.0374)).willReturn(null);

            UserLocationUpdateResponse result = userService.updateLocation(userId, request);

            assertThat(result.latitude()).isEqualTo(37.5563);
            assertThat(result.neighborhood()).isNull();
        }

        @Test
        void 유저_없음_예외() {
            UserLocationUpdateRequest request = new UserLocationUpdateRequest(37.5563, 127.0374);
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateLocation(userId, request))
                    .isInstanceOf(UserNotFoundException.class);
            then(neighborhoodPort).shouldHaveNoInteractions();
        }
    }

    @Nested
    class RegisterInterests {

        @Test
        void 성공() {
            UserInterestRequest request = new UserInterestRequest(List.of("#독서", "#카페투어"));
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            userService.registerInterests(userId, request);

            assertThat(user.isInterestRegistered()).isTrue();
            then(userEventProducer).should().sendUserInterestsRegistered(
                    UserInterestsRegisteredEvent.of(userId, request.hashtags()));
        }

        @Test
        void 유저_없음_예외() {
            UserInterestRequest request = new UserInterestRequest(List.of("#독서"));
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.registerInterests(userId, request))
                    .isInstanceOf(UserNotFoundException.class);
            then(userEventProducer).shouldHaveNoInteractions();
        }
    }

    @Nested
    class DeleteAccount {

        @Test
        void 성공() {
            given(userRepository.findById(userId)).willReturn(Optional.of(user));

            userService.deleteAccount(userId);

            assertThat(user.isDeleted()).isTrue();
            then(tokenManager).should().deleteToken(userId);
        }

        @Test
        void 유저_없음_예외() {
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.deleteAccount(userId))
                    .isInstanceOf(UserNotFoundException.class);
            then(tokenManager).shouldHaveNoInteractions();
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void 성공() {
            UserPasswordChangeRequest request =
                    new UserPasswordChangeRequest("currentPw", "newPw");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("currentPw", user.getPassword())).willReturn(true);
            given(passwordEncoder.encode("newPw")).willReturn("encodedNewPw");

            userService.changePassword(userId, request);

            assertThat(user.getPassword()).isEqualTo("encodedNewPw");
        }

        @Test
        void 유저_없음_예외() {
            UserPasswordChangeRequest request =
                    new UserPasswordChangeRequest("currentPw", "newPw");
            given(userRepository.findById(userId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void 현재_비밀번호_불일치_예외() {
            UserPasswordChangeRequest request =
                    new UserPasswordChangeRequest("wrongPw", "newPw");
            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPw", user.getPassword())).willReturn(false);

            assertThatThrownBy(() -> userService.changePassword(userId, request))
                    .isInstanceOf(InvalidPasswordException.class);
        }
    }
}
