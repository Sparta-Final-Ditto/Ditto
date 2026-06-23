package com.sparta.ditto.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.sparta.ditto.user.domain.follow.Follow;
import com.sparta.ditto.user.domain.follow.exception.AlreadyFollowingException;
import com.sparta.ditto.user.domain.follow.exception.CannotSelfFollowException;
import com.sparta.ditto.user.domain.follow.exception.NotFollowingException;
import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.repository.FollowRepository;
import com.sparta.ditto.user.infrastructure.repository.UserRepository;
import com.sparta.ditto.user.presentation.dto.response.UserPublicProfileResponse;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @InjectMocks
    private FollowService followService;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    private User follower;
    private User following;
    private UUID followerId;
    private UUID followingId;

    @BeforeEach
    void setUp() {
        followerId = UUID.randomUUID();
        followingId = UUID.randomUUID();
        follower = User.createEmailUser(
                "follower@test.com", "encodedPw", "followerNick", Gender.MALE, LocalDate.of(1990, 1, 1));
        following = User.createEmailUser(
                "following@test.com", "encodedPw", "followingNick", Gender.FEMALE, LocalDate.of(1995, 1, 1));
        ReflectionTestUtils.setField(follower, "id", followerId);
        ReflectionTestUtils.setField(following, "id", followingId);
    }

    @Nested
    class FollowUser {

        @Test
        void 성공() {
            given(followRepository.existsByFollowerIdAndFollowingId(followerId, followingId))
                    .willReturn(false);
            given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
            given(userRepository.findById(followingId)).willReturn(Optional.of(following));

            followService.follow(followerId, followingId);

            then(followRepository).should().save(any(Follow.class));
        }

        @Test
        void 자기_자신_팔로우_예외() {
            assertThatThrownBy(() -> followService.follow(followerId, followerId))
                    .isInstanceOf(CannotSelfFollowException.class);
        }

        @Test
        void 이미_팔로우_예외() {
            given(followRepository.existsByFollowerIdAndFollowingId(followerId, followingId))
                    .willReturn(true);

            assertThatThrownBy(() -> followService.follow(followerId, followingId))
                    .isInstanceOf(AlreadyFollowingException.class);
        }

        @Test
        void 팔로워_유저_없음_예외() {
            given(followRepository.existsByFollowerIdAndFollowingId(followerId, followingId))
                    .willReturn(false);
            given(userRepository.findById(followerId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> followService.follow(followerId, followingId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void 팔로잉_유저_없음_예외() {
            given(followRepository.existsByFollowerIdAndFollowingId(followerId, followingId))
                    .willReturn(false);
            given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
            given(userRepository.findById(followingId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> followService.follow(followerId, followingId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class UnfollowUser {

        @Test
        void 성공() {
            Follow follow = Follow.of(follower, following);
            given(followRepository.findByFollowerIdAndFollowingId(followerId, followingId))
                    .willReturn(Optional.of(follow));

            followService.unfollow(followerId, followingId);

            then(followRepository).should().delete(follow);
        }

        @Test
        void 자기_자신_언팔로우_예외() {
            assertThatThrownBy(() -> followService.unfollow(followerId, followerId))
                    .isInstanceOf(CannotSelfFollowException.class);
        }

        @Test
        void 팔로우_관계_없음_예외() {
            given(followRepository.findByFollowerIdAndFollowingId(followerId, followingId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> followService.unfollow(followerId, followingId))
                    .isInstanceOf(NotFollowingException.class);
        }
    }

    @Nested
    class GetFollowers {

        @Test
        void 성공() {
            given(followRepository.findFollowersByUserId(followingId))
                    .willReturn(List.of(follower));

            List<UserPublicProfileResponse> result = followService.getFollowers(followingId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).nickname()).isEqualTo("followerNick");
        }

        @Test
        void 팔로워_없음_빈_목록() {
            given(followRepository.findFollowersByUserId(followingId)).willReturn(List.of());

            List<UserPublicProfileResponse> result = followService.getFollowers(followingId);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class GetFollowings {

        @Test
        void 성공() {
            given(followRepository.findFollowingsByUserId(followerId))
                    .willReturn(List.of(following));

            List<UserPublicProfileResponse> result = followService.getFollowings(followerId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).nickname()).isEqualTo("followingNick");
        }

        @Test
        void 팔로잉_없음_빈_목록() {
            given(followRepository.findFollowingsByUserId(followerId)).willReturn(List.of());

            List<UserPublicProfileResponse> result = followService.getFollowings(followerId);

            assertThat(result).isEmpty();
        }
    }
}