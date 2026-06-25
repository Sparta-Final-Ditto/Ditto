package com.sparta.ditto.match.application.port;

import java.util.Optional;
import java.util.UUID;

public interface ExplanationCachePort {
    Optional<String> getExplanation(UUID userId, UUID matchedUserId);
    void saveExplanation(UUID userId, UUID matchedUserId, String explanation);
}