package com.sparta.ditto.chat.application.participant;

import java.util.UUID;

public interface ChatParticipantValidator {

    // 메시지 저장/조회/삭제/읽음 처리 전 요청자가 현재 참여자인지 검증
    void ensureActiveParticipant(UUID roomId, UUID userId);
}
