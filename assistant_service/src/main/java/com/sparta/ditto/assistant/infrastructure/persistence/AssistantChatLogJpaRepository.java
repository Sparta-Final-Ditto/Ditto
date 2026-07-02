package com.sparta.ditto.assistant.infrastructure.persistence;

import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantChatLogJpaRepository extends JpaRepository<AssistantChatLog, UUID> {
}
