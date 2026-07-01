package com.sparta.ditto.notification.presentation.dto.response;

public record UnreadCountResponse(long unreadCount) {

    public static UnreadCountResponse from(long unreadCount) {
        return new UnreadCountResponse(unreadCount);
    }
}