package com.sparta.ditto.user.infrastructure.kafka;

import java.util.UUID;

public record UserCreatedEvent(
        String eventType,
        UUID userId,
        String gender,
        String birthdate
) {
    public static UserCreatedEvent of(UUID userId, String gender, String birthdate) {
        return new UserCreatedEvent("USER_CREATED", userId, gender, birthdate);
    }
}
