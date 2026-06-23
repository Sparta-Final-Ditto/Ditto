package com.sparta.ditto.chat.infrastructure.redis;

import com.sparta.ditto.chat.application.room.port.ChatPresencePort;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatPresenceRedisAdapter implements ChatPresencePort {

    private final ChatPresenceRedisRepository chatPresenceRedisRepository;

    @Override
    public void refreshOnline(UUID userId) {
        runRedisOperation("refreshOnline", userId, null,
                () -> chatPresenceRedisRepository.refreshOnline(userId));
    }

    @Override
    public void enterRoom(UUID userId, UUID roomId) {
        runRedisOperation("enterRoom", userId, roomId,
                () -> chatPresenceRedisRepository.enterRoom(userId, roomId));
    }

    @Override
    public void leaveRoomIfCurrent(UUID userId, UUID roomId) {
        runRedisOperation("leaveRoomIfCurrent", userId, roomId,
                () -> chatPresenceRedisRepository.leaveRoomIfCurrent(userId, roomId));
    }

    @Override
    public void refreshActiveRoomTtlIfPresent(UUID userId) {
        runRedisOperation("refreshActiveRoomTtlIfPresent", userId, null,
                () -> chatPresenceRedisRepository.refreshActiveRoomTtlIfPresent(userId));
    }

    @Override
    public Optional<UUID> findActiveRoomId(UUID userId) {
        try {
            return chatPresenceRedisRepository.findActiveRoomId(userId);
        } catch (DataAccessException ex) {
            log.error(
                    "Failed to read chat presence from Redis. operation={}, userId={}",
                    "findActiveRoomId",
                    userId,
                    ex
            );
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void runRedisOperation(
            String operation,
            UUID userId,
            UUID roomId,
            Runnable operationRunner
    ) {
        try {
            operationRunner.run();
        } catch (DataAccessException ex) {
            log.error(
                    "Failed to update chat presence in Redis. operation={}, userId={}, roomId={}",
                    operation,
                    userId,
                    roomId,
                    ex
            );
            throw new BusinessException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
