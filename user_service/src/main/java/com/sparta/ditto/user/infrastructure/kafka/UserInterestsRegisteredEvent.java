package com.sparta.ditto.user.infrastructure.kafka;

import java.util.List;
import java.util.UUID;

public record UserInterestsRegisteredEvent(
        String eventType,
        UUID userId,
        List<String> hashtags
) {
    public static UserInterestsRegisteredEvent of(UUID userId, List<String> hashtags) {
        return new UserInterestsRegisteredEvent("USER_INTERESTS_REGISTERED", userId, hashtags);
    }
}
