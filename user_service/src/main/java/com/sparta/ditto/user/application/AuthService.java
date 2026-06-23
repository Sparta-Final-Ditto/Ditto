package com.sparta.ditto.user.application;

import com.sparta.ditto.user.domain.user.User;
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
import com.sparta.ditto.user.presentation.dto.request.AuthLoginRequest;
import com.sparta.ditto.user.presentation.dto.request.AuthReissueRequest;
import com.sparta.ditto.user.presentation.dto.request.AuthSignupRequest;
import com.sparta.ditto.user.presentation.dto.response.AuthTokenResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final TokenManager tokenManager;
    private final PasswordEncoder passwordEncoder;
    private final UserEventProducer userEventProducer;

    @Transactional
    public void signup(AuthSignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByNickname(request.nickname())) {
            throw new NicknameAlreadyExistsException();
        }
        // TODO: OAuth 추가 시 분기 추가 필요
        User user = User.createEmailUser(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.gender(),
                request.birthdate()
        );
        userRepository.save(user);

        userEventProducer.sendUserCreated(
                UserCreatedEvent.of(
                        user.getId(), user.getGender().name(), user.getBirthdate().toString()));
    }

    @Transactional
    public AuthTokenResponse login(AuthLoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() == UserStatus.BANNED) {
            throw new UserBannedException();
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidPasswordException();
        }

        user.updateLastLoginAt();
        return tokenManager.issueTokens(user);
    }

    public void logout(UUID userId) {
        tokenManager.deleteToken(userId);
    }

    public AuthTokenResponse reissue(AuthReissueRequest request) {
        UUID userId = tokenManager.validateRefreshToken(request.refreshToken());

        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() == UserStatus.BANNED) {
            throw new UserBannedException();
        }

        return tokenManager.issueTokens(user);
    }
}
