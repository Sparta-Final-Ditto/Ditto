package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatPresenceCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatPresenceResult;
import com.sparta.ditto.chat.application.room.port.ChatPresencePort;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatPresenceService {

    private final ChatParticipantValidator chatParticipantValidator;
    private final ChatPresencePort chatPresencePort;

    public ChatPresenceResult updatePresence(ChatPresenceCommand command) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        return switch (command.status()) {
            case ENTER -> enterRoom(command);
            case LEAVE -> leaveRoom(command);
        };
    }

    public void refreshHeartbeat(UUID userId) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        runRedisUpdate("heartbeat", userId, null, () -> {
            chatPresencePort.refreshOnline(userId);
            chatPresencePort.refreshActiveRoomTtlIfPresent(userId);
        });
    }

    private ChatPresenceResult enterRoom(ChatPresenceCommand command) {
        chatParticipantValidator.ensureRoomActive(command.roomId());
        chatParticipantValidator.ensureActiveParticipant(command.roomId(), command.requesterId());
        runRedisUpdate("enter", command.requesterId(), command.roomId(), () -> {
            chatPresencePort.refreshOnline(command.requesterId());
            chatPresencePort.enterRoom(command.requesterId(), command.roomId());
        });
        return ChatPresenceResult.of(command.roomId(), command.status());
    }

    private ChatPresenceResult leaveRoom(ChatPresenceCommand command) {
        runRedisUpdate(
                "leave",
                command.requesterId(),
                command.roomId(),
                () -> chatPresencePort.leaveRoomIfCurrent(
                        command.requesterId(),
                        command.roomId()
                )
        );
        return ChatPresenceResult.of(command.roomId(), command.status());
    }

    private void runRedisUpdate(
            String operation,
            UUID userId,
            UUID roomId,
            Runnable redisOperation
    ) {
        try {
            redisOperation.run();
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
