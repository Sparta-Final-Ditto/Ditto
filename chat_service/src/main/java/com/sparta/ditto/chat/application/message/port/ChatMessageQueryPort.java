package com.sparta.ditto.chat.application.message.port;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageQueryPort {

    Optional<SentMessage> findByMessageId(String messageId);

    Optional<SentMessage> findByMessageIdAndRoomId(String messageId, UUID roomId);

    List<SentMessage> findLatestByRoomId(UUID roomId, int limit);

    List<SentMessage> findBeforeCursor(
            UUID roomId,
            Instant cursorCreatedAt,
            String cursorMessageId,
            int limit
    );

    List<SentMessage> findAfterCursor(
            UUID roomId,
            Instant cursorCreatedAt,
            String cursorMessageId,
            int limit
    );

    // 나간 사용자: [joinedAt, upper] 범위 내 최신 N개
    List<SentMessage> findLatestWithinRange(
            UUID roomId, Instant joinedAt,
            Instant upperCreatedAt, String upperMessageId, int limit);

    // 나간 사용자: [joinedAt, beforeCursor) 범위 내
    List<SentMessage> findBeforeCursorWithinRange(
            UUID roomId, Instant joinedAt,
            Instant cursorCreatedAt, String cursorMessageId, int limit);

    // 나간 사용자: (afterCursor, upper] 범위 내, joinedAt 이후
    List<SentMessage> findAfterCursorWithinRange(
            UUID roomId, Instant joinedAt,
            Instant afterCreatedAt, String afterMessageId,
            Instant upperCreatedAt, String upperMessageId, int limit);

    // 방 목록 lastMessage 본문 채우기용 batch 조회
    List<SentMessage> findByMessageIds(Collection<String> messageIds);

    // 중복키 흡수: 유니크 키(roomId+senderId+clientMessageId)로 기존 사용자 메시지 조회
    Optional<SentMessage> findByRoomIdAndSenderIdAndClientMessageId(
            UUID roomId, UUID senderId, UUID clientMessageId);
}
