package com.sparta.ditto.user.application;

import com.sparta.ditto.user.domain.block.Block;
import com.sparta.ditto.user.domain.block.exception.AlreadyBlockedException;
import com.sparta.ditto.user.domain.block.exception.CannotSelfBlockException;
import com.sparta.ditto.user.domain.block.exception.NotBlockedException;
import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.repository.BlockRepository;
import com.sparta.ditto.user.infrastructure.repository.FollowRepository;
import com.sparta.ditto.user.infrastructure.repository.UserRepository;
import com.sparta.ditto.user.presentation.dto.response.UserPublicProfileResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BlockService {

    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new CannotSelfBlockException();
        }
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            throw new AlreadyBlockedException();
        }

        User blocker = userRepository.findById(blockerId).orElseThrow(UserNotFoundException::new);
        User blocked = userRepository.findById(blockedId).orElseThrow(UserNotFoundException::new);

        blockRepository.save(Block.of(blocker, blocked));

        followRepository.findByFollowerIdAndFollowingId(blockerId, blockedId)
                .ifPresent(followRepository::delete);
        followRepository.findByFollowerIdAndFollowingId(blockedId, blockerId)
                .ifPresent(followRepository::delete);
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new CannotSelfBlockException();
        }

        Block block = blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .orElseThrow(NotBlockedException::new);

        blockRepository.delete(block);
    }

    public List<UserPublicProfileResponse> getBlockedUsers(UUID blockerId) {
        return blockRepository.findBlockedUsersByBlockerId(blockerId).stream()
                .map(UserPublicProfileResponse::from)
                .toList();
    }
}
