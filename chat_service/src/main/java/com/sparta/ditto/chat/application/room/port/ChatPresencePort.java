package com.sparta.ditto.chat.application.room.port;

import java.util.Optional;
import java.util.UUID;

public interface ChatPresencePort {

    void refreshOnline(UUID userId);

    void enterRoom(UUID userId, UUID roomId);

    void leaveRoomIfCurrent(UUID userId, UUID roomId);

    void refreshActiveRoomTtlIfPresent(UUID userId);

    Optional<UUID> findActiveRoomId(UUID userId);
}
