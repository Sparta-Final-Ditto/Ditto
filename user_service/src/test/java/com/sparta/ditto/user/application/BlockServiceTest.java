package com.sparta.ditto.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

import com.sparta.ditto.user.domain.block.Block;
import com.sparta.ditto.user.domain.block.exception.AlreadyBlockedException;
import com.sparta.ditto.user.domain.block.exception.CannotSelfBlockException;
import com.sparta.ditto.user.domain.block.exception.NotBlockedException;
import com.sparta.ditto.user.domain.follow.Follow;
import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.enums.Gender;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.repository.BlockRepository;
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
class BlockServiceTest {

    @InjectMocks
    private BlockService blockService;

    @Mock
    private BlockRepository blockRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    private User blocker;
    private User blocked;
    private UUID blockerId;
    private UUID blockedId;

    @BeforeEach
    void setUp() {
        blockerId = UUID.randomUUID();
        blockedId = UUID.randomUUID();
        blocker = User.createEmailUser(
                "blocker@test.com", "encodedPw", "blockerNick", Gender.MALE, LocalDate.of(1990, 1, 1));
        blocked = User.createEmailUser(
                "blocked@test.com", "encodedPw", "blockedNick", Gender.FEMALE, LocalDate.of(1995, 1, 1));
        ReflectionTestUtils.setField(blocker, "id", blockerId);
        ReflectionTestUtils.setField(blocked, "id", blockedId);
    }

    @Nested
    class BlockUser {

        @Test
        void 성공_팔로우_관계_없음() {
            given(blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId))
                    .willReturn(false);
            given(userRepository.findById(blockerId)).willReturn(Optional.of(blocker));
            given(userRepository.findById(blockedId)).willReturn(Optional.of(blocked));
            given(followRepository.findByFollowerIdAndFollowingId(blockerId, blockedId))
                    .willReturn(Optional.empty());
            given(followRepository.findByFollowerIdAndFollowingId(blockedId, blockerId))
                    .willReturn(Optional.empty());

            blockService.block(blockerId, blockedId);

            then(blockRepository).should().save(any(Block.class));
            then(followRepository).should(never()).delete(any(Follow.class));
        }

        @Test
        void 성공_양방향_팔로우_삭제() {
            Follow forward = Follow.of(blocker, blocked);
            Follow reverse = Follow.of(blocked, blocker);
            given(blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId))
                    .willReturn(false);
            given(userRepository.findById(blockerId)).willReturn(Optional.of(blocker));
            given(userRepository.findById(blockedId)).willReturn(Optional.of(blocked));
            given(followRepository.findByFollowerIdAndFollowingId(blockerId, blockedId))
                    .willReturn(Optional.of(forward));
            given(followRepository.findByFollowerIdAndFollowingId(blockedId, blockerId))
                    .willReturn(Optional.of(reverse));

            blockService.block(blockerId, blockedId);

            then(followRepository).should().delete(forward);
            then(followRepository).should().delete(reverse);
        }

        @Test
        void 자기_자신_차단_예외() {
            assertThatThrownBy(() -> blockService.block(blockerId, blockerId))
                    .isInstanceOf(CannotSelfBlockException.class);
        }

        @Test
        void 이미_차단_예외() {
            given(blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId))
                    .willReturn(true);

            assertThatThrownBy(() -> blockService.block(blockerId, blockedId))
                    .isInstanceOf(AlreadyBlockedException.class);
        }

        @Test
        void 차단자_유저_없음_예외() {
            given(blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId))
                    .willReturn(false);
            given(userRepository.findById(blockerId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> blockService.block(blockerId, blockedId))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void 피차단자_유저_없음_예외() {
            given(blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId))
                    .willReturn(false);
            given(userRepository.findById(blockerId)).willReturn(Optional.of(blocker));
            given(userRepository.findById(blockedId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> blockService.block(blockerId, blockedId))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class UnblockUser {

        @Test
        void 성공() {
            Block block = Block.of(blocker, blocked);
            given(blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId))
                    .willReturn(Optional.of(block));

            blockService.unblock(blockerId, blockedId);

            then(blockRepository).should().delete(block);
        }

        @Test
        void 자기_자신_차단해제_예외() {
            assertThatThrownBy(() -> blockService.unblock(blockerId, blockerId))
                    .isInstanceOf(CannotSelfBlockException.class);
        }

        @Test
        void 차단_관계_없음_예외() {
            given(blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> blockService.unblock(blockerId, blockedId))
                    .isInstanceOf(NotBlockedException.class);
        }
    }

    @Nested
    class GetBlockedUsers {

        @Test
        void 성공() {
            given(blockRepository.findBlockedUsersByBlockerId(blockerId))
                    .willReturn(List.of(blocked));

            List<UserPublicProfileResponse> result = blockService.getBlockedUsers(blockerId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).nickname()).isEqualTo("blockedNick");
        }

        @Test
        void 차단_목록_없음_빈_목록() {
            given(blockRepository.findBlockedUsersByBlockerId(blockerId)).willReturn(List.of());

            List<UserPublicProfileResponse> result = blockService.getBlockedUsers(blockerId);

            assertThat(result).isEmpty();
        }
    }
}