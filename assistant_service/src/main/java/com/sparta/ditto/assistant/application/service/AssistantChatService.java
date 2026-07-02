package com.sparta.ditto.assistant.application.service;

import com.sparta.ditto.assistant.application.dto.command.AskAssistantCommand;
import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import com.sparta.ditto.assistant.domain.exception.LlmResponseFailedException;
import com.sparta.ditto.assistant.domain.repository.AssistantChatLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssistantChatService {

    private final ChatClient chatClient;
    private final AssistantChatLogRepository chatLogRepository;

    public AssistantAnswerResult ask(AskAssistantCommand command) {
        String answer;
        try {
            answer = chatClient.prompt()
                    .user(command.question())
                    .call()
                    .content();
        } catch (Exception e) {
            throw new LlmResponseFailedException();
        }

        // TODO: QuestionAnswerAdvisor 응답 메타데이터에서 검색된 출처 문서 추출
        List<AssistantAnswerResult.SourceResult> sources = List.of();

        chatLogRepository.save(AssistantChatLog.of(
                command.userId(), command.question(), answer, List.of(), List.of()));

        return new AssistantAnswerResult(answer, sources);
    }
}
