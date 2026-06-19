package com.sparta.ditto.chat.presentation.dto.response;

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
}