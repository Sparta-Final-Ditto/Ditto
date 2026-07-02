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

        // 같은 방 lastMessage를 동시에 갱신하는 트랜잭션 간 lost update를 막기 위해
        // 방 row에 쓰기 락을 걸어 갱신을 직렬화한다(도메인의 역전 방어 비교와 함께 동작).
        ChatRoom chatRoom = chatRoomPort.findByIdForUpdate(roomId)
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
