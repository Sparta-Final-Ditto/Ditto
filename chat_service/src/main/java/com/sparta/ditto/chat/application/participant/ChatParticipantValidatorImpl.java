package com.sparta.ditto.chat.application.participant;

import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
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
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        if (chatRoom.getStatus() == RoomStatus.INACTIVE) {
            throw new BusinessException(ChatErrorCode.CHAT_ROOM_INACTIVE);
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
            return new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND);
        }
        return new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT);
    }
}
