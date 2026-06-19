package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.domain.exception.ChatErrorCode;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageDocument;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageMongoRepository;
import com.sparta.ditto.chat.presentation.dto.response.ChatMessageCursorResponse;
import com.sparta.ditto.chat.presentation.dto.response.ChatMessageResponse;
import com.sparta.ditto.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private static final int DEFAULT_SIZE = 30;
    private static final int MAX_SIZE = 100;

    private final ChatMessageMongoRepository chatMessageMongoRepository;
    private final ChatParticipantValidator chatParticipantValidator;

    // 이전 메시지 조회. 응답은 오래된 순(ASC)으로 통일한다.
    public ChatMessageCursorResponse getPreviousMessages(
            UUID roomId, String before, Integer size, UUID requesterId) {

        chatParticipantValidator.ensureActiveParticipant(roomId, requesterId);

        int pageSize = normalizeSize(size);
        // hasNext 판단을 위해 요청보다 한 건 더 조회한다.
        Limit limit = Limit.of(pageSize + 1);

        List<ChatMessageDocument> rows = (before == null || before.isBlank())
                ? chatMessageMongoRepository.findLatestByRoomId(roomId, limit)
                : findBefore(roomId, before, limit);

        boolean hasNext = rows.size() > pageSize;
        if (hasNext) {
            rows = rows.subList(0, pageSize);
        }

        // Repository는 최신순(DESC)으로 주므로, 화면 표시용으로 오래된 순(ASC)으로 뒤집는다.
        List<ChatMessageResponse> items = new ArrayList<>(rows.size());
        for (ChatMessageDocument row : rows) {
            items.add(ChatMessageResponse.from(row));
        }
        Collections.reverse(items);

        // 다음 페이지 조회용 cursor = 이번 페이지에서 가장 오래된 messageId
        String nextCursor = (hasNext && !items.isEmpty()) ? items.get(0).messageId() : null;

        return ChatMessageCursorResponse.of(items, nextCursor, hasNext);
    }

    // 누락 메시지 조회. after 이후의 메시지를 오래된 순(ASC)으로 반환한다.
    public ChatMessageCursorResponse getMissedMessages(
            UUID roomId, String after, Integer size, UUID requesterId) {

        chatParticipantValidator.ensureActiveParticipant(roomId, requesterId);

        int pageSize = normalizeSize(size);
        Limit limit = Limit.of(pageSize + 1);

        ChatMessageDocument cursor = resolveCursor(roomId, after);
        List<ChatMessageDocument> rows = chatMessageMongoRepository.findAfterCursor(
                roomId, cursor.getCreatedAt(), cursor.getMessageId(), limit);

        boolean hasNext = rows.size() > pageSize;
        if (hasNext) {
            rows = rows.subList(0, pageSize);
        }

        List<ChatMessageResponse> items = toResponses(rows);

        String nextCursor = (hasNext && !items.isEmpty())
                ? items.get(items.size() - 1).messageId() : null;

        return ChatMessageCursorResponse.of(items, nextCursor, hasNext);
    }

    private List<ChatMessageDocument> findBefore(UUID roomId, String before, Limit limit) {
        // cursor 메시지가 해당 방에 실제 존재하는지 검증하고 createdAt을 확보한다.
        ChatMessageDocument cursor = chatMessageMongoRepository
                .findByMessageIdAndRoomId(before, roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND));
        return chatMessageMongoRepository.findBeforeCursor(
                roomId, cursor.getCreatedAt(), cursor.getMessageId(), limit);
    }

    private ChatMessageDocument resolveCursor(UUID roomId, String messageId) {
        return chatMessageMongoRepository.findByMessageIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new BusinessException(ChatErrorCode.CHAT_MESSAGE_NOT_FOUND));
    }

    private List<ChatMessageResponse> toResponses(List<ChatMessageDocument> rows) {
        List<ChatMessageResponse> items = new ArrayList<>(rows.size());
        for (ChatMessageDocument row : rows) {
            items.add(ChatMessageResponse.from(row));
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
