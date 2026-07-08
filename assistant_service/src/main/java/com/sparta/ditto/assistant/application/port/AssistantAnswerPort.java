package com.sparta.ditto.assistant.application.port;

import java.util.List;
import java.util.UUID;

/** RAG 기반 LLM 응답 생성을 담당하는 포트
 *  Infrastructure에서 구현 
*/
public interface AssistantAnswerPort {

    AssistantAnswer ask(String question);

    record AssistantAnswer(String answerText, List<RetrievedDocument> retrievedDocuments) {
    }

    record RetrievedDocument(UUID id, String title, String sourceType, Float similarityScore) {
    }
}
