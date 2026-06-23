package com.sparta.ditto.chat.application.participant;

import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoomInactiveException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.common.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatParticipantValidatorImpl implements ChatParticipantValidator {

    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;

    @Override
    @Transactional(readOnly = true)
    public void ensureActiveParticipant(UUID roomId, UUID userId) {
        chatRoomParticipantPort.findActiveParticipant(roomId, userId)
                .orElseThrow(() -> participantException(roomId));
    }

    @Override
    @Transactional(readOnly = true)
    public void ensureRoomActive(UUID roomId) {
        ChatRoom chatRoom = chatRoomPort.findById(roomId)
                .orElseThrow(() -> new ChatRoomNotFoundException());

        if (chatRoom.getStatus() == RoomStatus.INACTIVE) {
            throw new ChatRoomInactiveException();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomParticipant getParticipant(UUID roomId, UUID userId) {
        return chatRoomParticipantPort.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> participantException(roomId));
    }

    private BusinessException participantException(UUID roomId) {
        if (!chatRoomPort.existsById(roomId)) {
            return new ChatRoomNotFoundException();
        }
        return new ChatNotParticipantException();
    }
}
