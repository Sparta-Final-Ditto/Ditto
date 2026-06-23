package com.sparta.ditto.chat.application.message.dto.result;

import java.util.List;

public record ChatMessageCursorResult(
        List<ChatMessageResult> items,
        String nextCursor,
        boolean hasNext
) {
    public static ChatMessageCursorResult of(
            List<ChatMessageResult> items,
            String nextCursor,
            boolean hasNext
    ) {
        return new ChatMessageCursorResult(items, nextCursor, hasNext);
    }
}
