package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            throw new BusinessException(ChatErrorCode.CHAT_ROOM_INACTIVE);
        }

        // TODO: 재처리/비동기 흐름이 들어오면 오래된 메시지가 최신 메시지를 덮지 않도록
        // createdAt + messageId 복합 기준으로 역전 방어를 추가한다.
        chatRoom.updateLastMessage(messageId, messageCreatedAt);
    }
}
