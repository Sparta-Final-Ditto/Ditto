package com.sparta.ditto.assistant.infrastructure.persistence;

import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import com.sparta.ditto.assistant.domain.repository.AssistantChatLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AssistantChatLogRepositoryImpl implements AssistantChatLogRepository {

    private final AssistantChatLogJpaRepository jpaRepository;

    @Override
    public AssistantChatLog save(AssistantChatLog chatLog) {
        return jpaRepository.save(chatLog);
    }
}
