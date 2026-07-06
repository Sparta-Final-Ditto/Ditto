package com.sparta.ditto.assistant.application.service;

import com.sparta.ditto.assistant.application.dto.command.AskAssistantCommand;
import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import com.sparta.ditto.assistant.application.port.AssistantAnswerPort;
import com.sparta.ditto.assistant.application.port.AssistantAnswerPort.AssistantAnswer;
import com.sparta.ditto.assistant.application.port.AssistantAnswerPort.RetrievedDocument;
import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import com.sparta.ditto.assistant.domain.repository.AssistantChatLogRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatService {

    private final AssistantAnswerPort assistantAnswerPort;
    private final AssistantChatLogRepository chatLogRepository;

    public AssistantAnswerResult ask(AskAssistantCommand command) {
        AssistantAnswer answer = assistantAnswerPort.ask(command.question());

        List<AssistantAnswerResult.SourceResult> sources = answer.retrievedDocuments().stream()
                .map(doc -> new AssistantAnswerResult.SourceResult(doc.title(), doc.sourceType()))
                .toList();

        saveChatLog(command, answer);

        return new AssistantAnswerResult(answer.answerText(), sources);
    }

    private void saveChatLog(AskAssistantCommand command, AssistantAnswer answer) {
        try {
            List<UUID> matchedDocumentIds = answer.retrievedDocuments().stream()
                    .map(RetrievedDocument::id)
                    .toList();
            List<Float> similarityScores = answer.retrievedDocuments().stream()
                    .map(RetrievedDocument::similarityScore)
                    .toList();

            chatLogRepository.save(AssistantChatLog.of(
                    command.userId(), command.question(), answer.answerText(),
                    matchedDocumentIds, similarityScores));
        } catch (Exception e) {
            log.error("채팅 로그 저장 실패: question={}", command.question(), e);
        }
    }
}
