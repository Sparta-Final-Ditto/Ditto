package com.sparta.ditto.user.application;

import com.sparta.ditto.user.domain.follow.Follow;
import com.sparta.ditto.user.domain.follow.exception.AlreadyFollowingException;
import com.sparta.ditto.user.domain.follow.exception.CannotSelfFollowException;
import com.sparta.ditto.user.domain.follow.exception.NotFollowingException;
import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.repository.FollowRepository;
import com.sparta.ditto.user.infrastructure.repository.UserRepository;

import java.util.List;
import java.util.UUID;

import com.sparta.ditto.user.presentation.dto.response.UserPublicProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    @Transactional
    public void follow(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw new CannotSelfFollowException();
        }
        if (followRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            throw new AlreadyFollowingException();
        }

        User follower = userRepository.findById(followerId).orElseThrow(UserNotFoundException::new);
        User following = userRepository.findById(followingId).orElseThrow(UserNotFoundException::new);

        followRepository.save(Follow.of(follower, following));
    }

    @Transactional
    public void unfollow(UUID followerId, UUID followingId) {
        if (followerId.equals(followingId)) {
            throw new CannotSelfFollowException();
        }

        Follow follow = followRepository.findByFollowerIdAndFollowingId(followerId, followingId)
                .orElseThrow(NotFollowingException::new);

        followRepository.delete(follow);
    }

    public List<UserPublicProfileResponse> getFollowers(UUID userId) {
        return followRepository.findFollowersByUserId(userId).stream()
                .map(UserPublicProfileResponse::from)
                .toList();
    }
}
