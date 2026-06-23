package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatPresenceCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatPresenceResult;
import com.sparta.ditto.chat.application.room.port.ChatPresencePort;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        chatPresencePort.refreshOnline(userId);
        chatPresencePort.refreshActiveRoomTtlIfPresent(userId);
    }

    private ChatPresenceResult enterRoom(ChatPresenceCommand command) {
        chatParticipantValidator.ensureRoomActive(command.roomId());
        chatParticipantValidator.ensureActiveParticipant(command.roomId(), command.requesterId());
        chatPresencePort.refreshOnline(command.requesterId());
        chatPresencePort.enterRoom(command.requesterId(), command.roomId());
        return ChatPresenceResult.of(command.roomId(), command.status());
    }

    private ChatPresenceResult leaveRoom(ChatPresenceCommand command) {
        chatPresencePort.leaveRoomIfCurrent(command.requesterId(), command.roomId());
        return ChatPresenceResult.of(command.roomId(), command.status());
    }
}
