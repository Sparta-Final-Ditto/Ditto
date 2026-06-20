package com.sparta.ditto.chat.infrastructure.jpa;

import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomParticipantRepository extends JpaRepository<ChatRoomParticipant, UUID> {

    // 참여자 메타데이터 조회용. 나간 참여자까지 포함해 알림 설정, 읽음 상태 등을 확인할 때 사용한다.
    Optional<ChatRoomParticipant> findByRoomIdAndUserId(UUID roomId, UUID userId);

    // 메시지 전송, 조회, 읽음 처리 전에 요청자가 현재 참여자인지 검증할 때 사용한다.
    Optional<ChatRoomParticipant> findByRoomIdAndUserIdAndLeftAtIsNull(UUID roomId, UUID userId);

    // 특정 채팅방의 현재 참여자 목록 조회와 알림 대상 후보 계산에 사용한다.
    List<ChatRoomParticipant> findAllByRoomIdAndLeftAtIsNull(UUID roomId);

    // 1:1 채팅방 재활성화 시 나간 참여자까지 포함해 참여자 상태를 복구할 때 사용한다.
    List<ChatRoomParticipant> findAllByRoomId(UUID roomId);

    // 내 채팅방 목록 조회에서 현재 참여 중인 방을 찾을 때 사용한다.
    List<ChatRoomParticipant> findAllByUserIdAndLeftAtIsNull(UUID userId);

    // 내 채팅방 기본 목록 조회에서 숨김 처리되지 않은 현재 참여 방만 찾을 때 사용한다.
    List<ChatRoomParticipant> findAllByUserIdAndLeftAtIsNullAndHiddenFalse(UUID userId);
}
