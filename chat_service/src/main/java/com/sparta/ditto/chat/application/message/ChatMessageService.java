package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.dto.result.ChatMessageCursorResult;
import com.sparta.ditto.chat.application.message.dto.result.ChatMessageResult;
import com.sparta.ditto.chat.application.message.dto.result.ChatMessageVisibilityRange;
import com.sparta.ditto.chat.application.message.port.ChatMessageCommandPort;
import com.sparta.ditto.chat.application.message.port.ChatMessageQueryPort;
import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.domain.exception.ChatMessageForbiddenException;
import com.sparta.ditto.chat.domain.exception.ChatMessageNotFoundException;

import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 100;

    private final ChatMessageCommandPort chatMessageCommandPort;
    private final ChatMessageQueryPort chatMessageQueryPort;
    private final ChatParticipantValidator chatParticipantValidator;
    private final ChatMessageVisibilityService chatMessageVisibilityService;

    // 이전 메시지 조회 응답은 오래된 메시지 -> 최신 메시지 순서로 통일한다.
    public ChatMessageCursorResult getPreviousMessages(
            UUID roomId, String before, Integer size, UUID requesterId) {

        ChatMessageVisibilityRange range =
                chatMessageVisibilityService.getVisibilityRange(roomId, requesterId);
        if (range.empty()) {                       // 나갔지만 볼 수 있는 메시지가 없음
            return ChatMessageCursorResult.of(List.of(), null, false);
        }

        int pageSize = normalizeSize(size);
        int limit = pageSize + 1;

        List<SentMessage> rows = range.hasUpperBound()
                ? findPreviousForLeftUser(roomId, before, range, limit)
                : findPreviousForActiveUser(roomId, before, limit);

        boolean hasNext = rows.size() > pageSize;
        if (hasNext) {
            rows = rows.subList(0, pageSize);
        }

        List<ChatMessageResult> items = toResults(rows);
        Collections.reverse(items);
        String nextCursor = (hasNext && !items.isEmpty()) ? items.get(0).messageId() : null;
        return ChatMessageCursorResult.of(items, nextCursor, hasNext);
    }

    private List<SentMessage> findPreviousForActiveUser(UUID roomId, String before, int limit) {
        return (before == null || before.isBlank())
                ? chatMessageQueryPort.findLatestByRoomId(roomId, limit)
                : findBefore(roomId, before, limit);
    }

    private List<SentMessage> findPreviousForLeftUser(
            UUID roomId, String before, ChatMessageVisibilityRange range, int limit) {
        Optional<SentMessage> upperOpt =
                chatMessageQueryPort.findByMessageIdAndRoomId(range.lastVisibleMessageId(), roomId);
        if (upperOpt.isEmpty()) {
            return List.of(); // lastVisible 메시지 누락 시 fail-closed (과거 조회 실패 대신 빈 결과)
        }
        SentMessage upper = upperOpt.get();

        if (before == null || before.isBlank()) {
            return chatMessageQueryPort.findLatestWithinRange(
                    roomId, range.joinedAt(), upper.createdAt(), upper.messageId(), limit);
        }
        SentMessage cursor = resolveCursor(roomId, before);   // 클라이언트 커서는 기존대로 검증
        if (isAfter(cursor, upper)) {
            return chatMessageQueryPort.findLatestWithinRange(
                    roomId, range.joinedAt(), upper.createdAt(), upper.messageId(), limit);
        }
        return chatMessageQueryPort.findBeforeCursorWithinRange(
                roomId, range.joinedAt(), cursor.createdAt(), cursor.messageId(), limit);
    }

    private boolean isAfter(SentMessage a, SentMessage b) {
        int cmp = a.createdAt().compareTo(b.createdAt());
        return cmp > 0 || (cmp == 0 && a.messageId().compareTo(b.messageId()) > 0);
    }

    // 누락 메시지는 after 이후 메시지를 오래된 메시지 -> 최신 메시지 순서로 반환한다.
    public ChatMessageCursorResult getMissedMessages(
            UUID roomId, String after, Integer size, UUID requesterId) {

        ChatMessageVisibilityRange range =
                chatMessageVisibilityService.getVisibilityRange(roomId, requesterId);
        if (range.empty()) {
            return ChatMessageCursorResult.of(List.of(), null, false);
        }

        int pageSize = normalizeSize(size);
        int limit = pageSize + 1;

        SentMessage cursor = resolveCursor(roomId, after);
        List<SentMessage> rows;
        if (range.hasUpperBound()) {
            Optional<SentMessage> upperOpt =
                    chatMessageQueryPort.findByMessageIdAndRoomId(range.lastVisibleMessageId(), roomId);
            if (upperOpt.isEmpty()) {
                return ChatMessageCursorResult.of(List.of(), null, false); // fail-closed
            }
            SentMessage upper = upperOpt.get();
            rows = chatMessageQueryPort.findAfterCursorWithinRange(
                    roomId, range.joinedAt(), cursor.createdAt(), cursor.messageId(),
                    upper.createdAt(), upper.messageId(), limit);
        } else {
            rows = chatMessageQueryPort.findAfterCursor(
                    roomId, cursor.createdAt(), cursor.messageId(), limit);
        }

        boolean hasNext = rows.size() > pageSize;
        if (hasNext) {
            rows = rows.subList(0, pageSize);
        }
        List<ChatMessageResult> items = toResults(rows);
        String nextCursor = (hasNext && !items.isEmpty())
                ? items.get(items.size() - 1).messageId() : null;
        return ChatMessageCursorResult.of(items, nextCursor, hasNext);
    }

    public void deleteMessage(UUID roomId, String messageId, UUID requesterId) {
        chatParticipantValidator.ensureActiveParticipant(roomId, requesterId);

        SentMessage message = chatMessageQueryPort.findByMessageIdAndRoomId(messageId, roomId)
                .orElseThrow(ChatMessageNotFoundException::new);

        if (!requesterId.equals(message.senderId())) {
            throw new ChatMessageForbiddenException();
        }

        chatMessageCommandPort.markDeleted(roomId, messageId);
    }

    private List<SentMessage> findBefore(UUID roomId, String before, int limit) {
        SentMessage cursor = chatMessageQueryPort.findByMessageIdAndRoomId(before, roomId)
                .orElseThrow(ChatMessageNotFoundException::new);
        return chatMessageQueryPort.findBeforeCursor(
                roomId, cursor.createdAt(), cursor.messageId(), limit);
    }

    private SentMessage resolveCursor(UUID roomId, String messageId) {
        return chatMessageQueryPort.findByMessageIdAndRoomId(messageId, roomId)
                .orElseThrow(ChatMessageNotFoundException::new);
    }

    private List<ChatMessageResult> toResults(List<SentMessage> rows) {
        List<ChatMessageResult> items = new ArrayList<>(rows.size());
        for (SentMessage row : rows) {
            items.add(ChatMessageResult.from(row));
        }
        return items;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
