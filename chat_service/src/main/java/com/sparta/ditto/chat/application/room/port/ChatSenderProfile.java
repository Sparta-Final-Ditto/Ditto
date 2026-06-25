package com.sparta.ditto.chat.application.room.port;

public record ChatSenderProfile(String nickname, String profileImageUrl) {

    public static ChatSenderProfile unknown() {
        return new ChatSenderProfile(null, null);
    }
}
