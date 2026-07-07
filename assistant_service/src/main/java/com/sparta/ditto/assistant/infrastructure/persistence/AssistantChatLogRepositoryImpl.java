package com.sparta.ditto.assistant.infrastructure.persistence;

import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import com.sparta.ditto.assistant.domain.repository.AssistantChatLogRepository;
import com.sparta.ditto.common.util.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AssistantChatLogRepositoryImpl implements AssistantChatLogRepository {

    private final AssistantChatLogJpaRepository jpaRepository;
    private final UuidV7Generator uuidV7Generator;

    @Override
    public AssistantChatLog save(AssistantChatLog chatLog) {
        AssistantChatLogJpaEntity saved = jpaRepository.save(
                AssistantChatLogJpaEntity.from(chatLog, uuidV7Generator.generate()));
        return saved.toDomain();
    }
}
