package com.sparta.ditto.chat.presentation.dto.response;

import com.sparta.ditto.chat.application.message.dto.result.ChatMessageCursorResult;
import java.util.List;

public record ChatMessageCursorResponse(
        List<ChatMessageResponse> items,
        String nextCursor,
        boolean hasNext
) {
    public static ChatMessageCursorResponse of(
            List<ChatMessageResponse> items, String nextCursor, boolean hasNext) {
        return new ChatMessageCursorResponse(items, nextCursor, hasNext);
    }

    public static ChatMessageCursorResponse from(ChatMessageCursorResult result) {
        return new ChatMessageCursorResponse(
                result.items().stream()
                        .map(ChatMessageResponse::from)
                        .toList(),
                result.nextCursor(),
                result.hasNext()
        );
    }
}
