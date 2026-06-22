package com.sparta.ditto.chat.application.message.port;

import java.util.UUID;

public interface ChatMessageDedupStore {

    // 중복 방지 시작: 신규 획득 / 완료된 중복(messageId) / 처리중 중복 구분
    DedupBeginResult begin(UUID roomId, UUID senderId, UUID clientMessageId);

    // 저장 성공 후 dedup 값을 messageId로 확정
    void complete(UUID roomId, UUID senderId, UUID clientMessageId, String messageId);

    // 저장 실패 등으로 처리를 못 끝냈을 때 PROCESSING 잠금 해제
    void release(UUID roomId, UUID senderId, UUID clientMessageId);
}
