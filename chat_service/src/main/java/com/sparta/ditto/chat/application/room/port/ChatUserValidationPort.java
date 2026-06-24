package com.sparta.ditto.chat.application.room.port;

import java.util.List;
import java.util.UUID;

public interface ChatUserValidationPort {

    void validateDirectChatTarget(UUID requesterId, UUID targetUserId);

    void validateGroupChatParticipants(UUID requesterId, List<UUID> participantIds);
}
