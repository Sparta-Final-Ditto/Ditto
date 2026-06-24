package com.sparta.ditto.chat.infrastructure.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.chat.application.room.port.ChatUserValidationPort;
import com.sparta.ditto.chat.domain.exception.ChatBlockedUserException;
import com.sparta.ditto.chat.domain.exception.ChatUserNotFoundException;
import com.sparta.ditto.chat.domain.exception.ChatUserValidationFailedException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceChatValidationAdapter implements ChatUserValidationPort {

    private static final String USER_NOT_FOUND_CODE = "USER-001";
    private static final String BLOCKED_USER_CODE = "BLOCK-004";

    private final UserServiceClient userServiceClient;
    private final CircuitBreaker userServiceChatValidationCircuitBreaker;
    private final ObjectMapper objectMapper;

    @Override
    public void validateDirectChatTarget(UUID requesterId, UUID targetUserId) {
        validateChatUsers(requesterId, List.of(targetUserId), true);
    }

    @Override
    public void validateGroupChatParticipants(UUID requesterId, List<UUID> participantIds) {
        Set<UUID> uniqueParticipantIds = new LinkedHashSet<>(participantIds);
        uniqueParticipantIds.remove(requesterId);
        validateChatUsers(requesterId, List.copyOf(uniqueParticipantIds), true);
    }

    private void validateChatUsers(
            UUID requesterId,
            List<UUID> targetUserIds,
            boolean checkBlock
    ) {
        try {
            userServiceChatValidationCircuitBreaker.executeRunnable(() ->
                    callUserService(requesterId, targetUserIds, checkBlock)
            );
        } catch (ChatUserNotFoundException | ChatBlockedUserException ex) {
            throw ex;
        } catch (FeignException ex) {
            throw mapFeignException(ex);
        } catch (CallNotPermittedException ex) {
            log.warn("User service chat validation circuit breaker is open. requesterId={}",
                    requesterId);
            throw new ChatUserValidationFailedException();
        } catch (RuntimeException ex) {
            log.warn("User service chat validation request failed. requesterId={}",
                    requesterId, ex);
            throw new ChatUserValidationFailedException();
        }
    }

    private void callUserService(
            UUID requesterId,
            List<UUID> targetUserIds,
            boolean checkBlock
    ) {
        try {
            userServiceClient.validateChatUsers(
                    new ChatUserValidationRequest(requesterId, targetUserIds, checkBlock)
            );
        } catch (FeignException ex) {
            RuntimeException businessException = mapBusinessException(ex);
            if (businessException != null) {
                throw businessException;
            }
            throw ex;
        }
    }

    private RuntimeException mapFeignException(FeignException ex) {
        RuntimeException businessException = mapBusinessException(ex);
        if (businessException != null) {
            throw businessException;
        }

        log.warn("User service chat validation rejected. status={}, errorCode={}",
                ex.status(), extractErrorCode(ex));
        throw new ChatUserValidationFailedException();
    }

    private RuntimeException mapBusinessException(FeignException ex) {
        String code = extractErrorCode(ex);
        if (USER_NOT_FOUND_CODE.equals(code)) {
            return new ChatUserNotFoundException();
        }
        if (BLOCKED_USER_CODE.equals(code)) {
            return new ChatBlockedUserException();
        }
        return null;
    }

    private String extractErrorCode(FeignException ex) {
        return ex.responseBody()
                .map(this::parseErrorCode)
                .orElse(null);
    }

    private String parseErrorCode(ByteBuffer responseBody) {
        try {
            String body = StandardCharsets.UTF_8.decode(responseBody).toString();
            JsonNode root = objectMapper.readTree(body);
            JsonNode code = root.get("code");
            return code == null ? null : code.asText();
        } catch (Exception parseException) {
            log.warn("Failed to parse user service validation error.");
            return null;
        }
    }
}
