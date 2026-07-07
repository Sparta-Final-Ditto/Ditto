package com.sparta.ditto.chat.application.room;

import java.util.UUID;

public record InvitedTarget(UUID userId, String nickname) {
}
