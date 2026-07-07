package com.sparta.ditto.chat.infrastructure.jpa;

import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying(flushAutomatically = true)
    @Query("""
            update ChatRoomParticipant participant
               set participant.unreadCount = participant.unreadCount + 1
             where participant.roomId = :roomId
               and participant.userId <> :senderId
               and participant.leftAt is null
            """)
    int incrementUnreadCountForActiveParticipantsExceptSender(
            @Param("roomId") UUID roomId,
            @Param("senderId") UUID senderId
    );

    // 읽음 처리: 읽은 위치 갱신 + unread_count 0을 하나의 atomic update로 처리한다.
    // dirty-checking(로드 시점 스냅샷 기반)으로 하면 그 사이 들어온 메시지의 unread 증가를
    // 덮어써 lost update가 발생하므로, DB에서 직접 갱신한다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ChatRoomParticipant participant
               set participant.lastReadMessageId = :lastReadMessageId,
                   participant.lastReadAt = :lastReadAt,
                   participant.unreadCount = 0
             where participant.roomId = :roomId
               and participant.userId = :userId
               and participant.leftAt is null
            """)
    int markReadAndResetUnread(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId,
            @Param("lastReadMessageId") String lastReadMessageId,
            @Param("lastReadAt") Instant lastReadAt
    );

    // OWNER 위임 등 권한 변경 시 동시 요청을 직렬화하기 위해 요청자 참여자 row에 쓰기 락을 건다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ChatRoomParticipant p "
            + "where p.roomId = :roomId and p.userId = :userId and p.leftAt is null")
    Optional<ChatRoomParticipant> findActiveParticipantForUpdate(
            @Param("roomId") UUID roomId, @Param("userId") UUID userId);
}
