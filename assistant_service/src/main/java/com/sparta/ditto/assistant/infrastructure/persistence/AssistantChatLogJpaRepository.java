package com.sparta.ditto.assistant.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantChatLogJpaRepository
        extends JpaRepository<AssistantChatLogJpaEntity, UUID> {
}
