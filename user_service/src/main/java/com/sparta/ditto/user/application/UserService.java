package com.sparta.ditto.user.application;

import com.sparta.ditto.user.domain.user.User;
import com.sparta.ditto.user.domain.user.exception.NicknameAlreadyExistsException;
import com.sparta.ditto.user.domain.user.exception.UserNotFoundException;
import com.sparta.ditto.user.infrastructure.repository.UserRepository;
import com.sparta.ditto.user.infrastructure.security.TokenManager;
import com.sparta.ditto.user.presentation.dto.request.UserUpdateRequest;
import com.sparta.ditto.user.presentation.dto.response.AuthTokenResponse;
import com.sparta.ditto.user.presentation.dto.response.UserUpdateResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final TokenManager tokenManager;

    @Transactional
    public UserUpdateResponse updateProfile(UUID userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        boolean nicknameChanged = request.nickname() != null
                && !request.nickname().isBlank()
                && !request.nickname().equals(user.getNickname());

        if (nicknameChanged && userRepository.existsByNickname(request.nickname())) {
            throw new NicknameAlreadyExistsException();
        }

        user.updateProfile(request.nickname(), request.bio(), request.profileImageUrl());

        AuthTokenResponse tokens = nicknameChanged ? tokenManager.issueTokens(user) : null;

        return new UserUpdateResponse(user.getNickname(), user.getBio(), user.getProfileImageUrl(), tokens);
    }
}
