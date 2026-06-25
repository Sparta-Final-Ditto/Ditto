package com.sparta.ditto.chat.application.room.port;

import java.util.UUID;

public interface ChatUserProfilePort {

    ChatSenderProfile findProfile(UUID userId);
}
