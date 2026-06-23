package com.sparta.ditto.chat.application.room.port;

import java.util.UUID;

// 채팅방 생명주기 이벤트를 외부로 발행하는 포트.
// 메시지 발행(ChatMessagePublisher)과 관심사가 달라 별도 포트로 분리했다.
public interface ChatRoomEventPublisher {

    // 나간 사용자에게 퇴장 신호를 발송한다.
    // 클라이언트는 이 신호를 받으면 room 채널 STOMP UNSUBSCRIBE를 호출해야 한다.
    void notifyLeft(UUID userId, UUID roomId);
}
