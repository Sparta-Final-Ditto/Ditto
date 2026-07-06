package com.sparta.ditto.assistant.infrastructure.llm;

import com.sparta.ditto.assistant.application.port.AssistantAnswerPort;
import com.sparta.ditto.assistant.domain.exception.LlmResponseFailedException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpringAiAssistantAnswerAdapter implements AssistantAnswerPort {

    private final ChatClient chatClient;

    @Override
    public AssistantAnswer ask(String question) {
        ChatClientResponse response;
        try {
            response = chatClient.prompt()
                    .user(question)
                    .call()
                    .chatClientResponse();
        } catch (Exception e) {
            log.error("LLM 호출 실패: question={}", question, e);
            throw new LlmResponseFailedException();
        }

        String answerText = response.chatResponse().getResult().getOutput().getText();
        List<RetrievedDocument> retrievedDocuments = extractRetrievedDocuments(response).stream()
                .map(doc -> new RetrievedDocument(
                        UUID.fromString(doc.getId()),
                        (String) doc.getMetadata().get("title"),
                        (String) doc.getMetadata().get("sourceType"),
                        doc.getScore() != null ? doc.getScore().floatValue() : null))
                .toList();

        return new AssistantAnswer(answerText, retrievedDocuments);
    }

    @SuppressWarnings("unchecked")
    private List<Document> extractRetrievedDocuments(ChatClientResponse response) {
        Object documents = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        return documents != null ? (List<Document>) documents : List.of();
    }
}
