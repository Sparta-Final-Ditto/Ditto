package com.sparta.ditto.chat.application.message.port;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import java.util.UUID;

public interface ChatMessagePublisher {

    // 발신자에게 저장 결과(ACK) 전달
    void ackToSender(UUID senderId, SentMessage message);

    // 채팅방 구독자에게 메시지 브로드캐스트
    void broadcast(UUID roomId, SentMessage message);
}
