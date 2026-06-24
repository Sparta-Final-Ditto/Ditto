package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.event.ChatMessageCreatedEvent;
import com.sparta.ditto.chat.application.event.ChatNotificationEventPublisher;
import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.room.ChatNotificationCandidateService;
import com.sparta.ditto.chat.application.room.port.ChatPresencePort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.room.RoomType;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageNotificationService {

    private final ChatNotificationCandidateService chatNotificationCandidateService;
    private final ChatPresencePort chatPresencePort;
    private final ChatRoomPort chatRoomPort;
    private final ChatNotificationEventPublisher chatNotificationEventPublisher;

    // 저장·브로드캐스트 완료 후 호출: 현재 방을 보고 있지 않은 수신자에게만 알림 이벤트 발행
    public void dispatch(SentMessage saved) {
        UUID roomId = saved.roomId();
        UUID senderId = saved.senderId();

        List<UUID> candidates =
                chatNotificationCandidateService.findNotificationCandidateUserIds(roomId, senderId);

        // active_room이 현재 방인 사용자 제외
        List<UUID> receiverIds = candidates.stream()
                .filter(userId -> !roomId.equals(
                        chatPresencePort.findActiveRoomId(userId).orElse(null)))
                .toList();

        if (receiverIds.isEmpty()) {
            log.debug("알림 대상 없음 — 발행 생략. roomId={}, messageId={}", roomId, saved.messageId());
            return;
        }

        RoomType roomType = chatRoomPort.findById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new)
                .getRoomType();

        chatNotificationEventPublisher.publish(
                ChatMessageCreatedEvent.of(saved, roomType, receiverIds));
    }
}
