package com.sparta.ditto.assistant.application.service;

import com.sparta.ditto.assistant.application.dto.command.AskAssistantCommand;
import com.sparta.ditto.assistant.application.dto.result.AssistantAnswerResult;
import com.sparta.ditto.assistant.domain.entity.AssistantChatLog;
import com.sparta.ditto.assistant.domain.exception.LlmResponseFailedException;
import com.sparta.ditto.assistant.domain.repository.AssistantChatLogRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantChatService {

    private final ChatClient chatClient;
    private final AssistantChatLogRepository chatLogRepository;

    public AssistantAnswerResult ask(AskAssistantCommand command) {
        ChatClientResponse response;
        try {
            response = chatClient.prompt()
                    .user(command.question())
                    .call()
                    .chatClientResponse();
        } catch (Exception e) {
            log.error("LLM 호출 실패: question={}", command.question(), e);
            throw new LlmResponseFailedException();
        }

        String answer = response.chatResponse().getResult().getOutput().getText();
        List<Document> retrievedDocuments = extractRetrievedDocuments(response);

        List<AssistantAnswerResult.SourceResult> sources = retrievedDocuments.stream()
                .map(doc -> new AssistantAnswerResult.SourceResult(
                        (String) doc.getMetadata().get("title"),
                        (String) doc.getMetadata().get("sourceType")))
                .toList();

        saveChatLog(command, answer, retrievedDocuments);

        return new AssistantAnswerResult(answer, sources);
    }

    private void saveChatLog(
            AskAssistantCommand command, String answer, List<Document> retrievedDocuments) {
        try {
            List<UUID> matchedDocumentIds = retrievedDocuments.stream()
                    .map(doc -> UUID.fromString(doc.getId()))
                    .toList();
            List<Float> similarityScores = retrievedDocuments.stream()
                    .map(doc -> doc.getScore() != null ? doc.getScore().floatValue() : null)
                    .toList();

            chatLogRepository.save(AssistantChatLog.of(
                    command.userId(), command.question(), answer,
                    matchedDocumentIds, similarityScores));
        } catch (Exception e) {
            log.error("채팅 로그 저장 실패: question={}", command.question(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Document> extractRetrievedDocuments(ChatClientResponse response) {
        Object documents = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        return documents != null ? (List<Document>) documents : List.of();
    }
}
