package com.sparta.ditto.chat.application.participant;

import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomRepository;
import com.sparta.ditto.common.exception.BusinessException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatParticipantValidatorImpl implements ChatParticipantValidator {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;

    @Override
    @Transactional(readOnly = true)
    public void ensureActiveParticipant(UUID roomId, UUID userId) {
        chatRoomParticipantRepository.findByRoomIdAndUserIdAndLeftAtIsNull(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }

    @Override
    @Transactional(readOnly = true)
    public void ensureRoomActive(UUID roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_ROOM_NOT_FOUND));

        if (chatRoom.getStatus() == RoomStatus.INACTIVE) {
            throw new BusinessException(ChatErrorCode.CHAT_ROOM_INACTIVE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomParticipant getParticipant(UUID roomId, UUID userId) {
        return chatRoomParticipantRepository.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_NOT_PARTICIPANT));
    }
}
