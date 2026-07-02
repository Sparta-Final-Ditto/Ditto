package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatRoomInactiveException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoomMetadataService {

    private final ChatRoomPort chatRoomPort;

    @Transactional
    public void updateLastMessage(
            UUID roomId,
            String messageId,
            Instant messageCreatedAt
    ) {
        if (roomId == null
                || messageId == null
                || messageId.isBlank()
                || messageCreatedAt == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        ChatRoom chatRoom = chatRoomPort.findById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);

        if (chatRoom.getStatus() == RoomStatus.INACTIVE) {
            throw new ChatRoomInactiveException();
        }

        // 오래된 메시지가 최신 lastMessage를 덮지 않게 하는 역전 방어는
        // ChatRoom.updateLastMessage 도메인 메서드에 있다(createdAt + messageId 기준).
        chatRoom.updateLastMessage(messageId, messageCreatedAt);
        log.debug("Chat room last message updated. roomId={}, messageId={}, messageCreatedAt={}",
                roomId, messageId, messageCreatedAt);
    }
}
