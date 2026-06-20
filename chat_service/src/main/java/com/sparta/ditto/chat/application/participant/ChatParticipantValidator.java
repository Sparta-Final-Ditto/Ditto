package com.sparta.ditto.chat.application.participant;

import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import java.util.UUID;

public interface ChatParticipantValidator {

    // 메시지 저장/조회/삭제/읽음 처리 전 요청자가 현재 참여자인지 검증
    void ensureActiveParticipant(UUID roomId, UUID userId);

    // 메시지 전송 전 채팅방이 활성 상태인지 검증
    void ensureRoomActive(UUID roomId);

    // 나간 참여자까지 포함해 알림 설정, 읽음 상태, visibility 기준을 조회할 때 사용
    ChatRoomParticipant getParticipant(UUID roomId, UUID userId);
}
