package com.sparta.ditto.assistant.domain.repository;

import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;

public interface AssistantChatLogRepository {

    AssistantChatLog save(AssistantChatLog chatLog);
}
