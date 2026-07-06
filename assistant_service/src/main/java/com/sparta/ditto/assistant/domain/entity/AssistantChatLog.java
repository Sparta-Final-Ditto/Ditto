package com.sparta.ditto.assistant.domain.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 질문/답변/근거 문서/유사도 로그. 정확도 평가(Hit@K, Faithfulness)용 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssistantChatLog {

    private UUID id;
    private UUID userId;
    private String question;
    private String answer;
    private List<UUID> matchedDocumentIds;
    private List<Float> similarityScores;
    private Instant createdAt;

    public static AssistantChatLog of(
            UUID userId,
            String question,
            String answer,
            List<UUID> matchedDocumentIds,
            List<Float> similarityScores
    ) {
        AssistantChatLog chatLog = new AssistantChatLog();
        chatLog.userId = userId;
        chatLog.question = question;
        chatLog.answer = answer;
        chatLog.matchedDocumentIds = matchedDocumentIds;
        chatLog.similarityScores = similarityScores;
        chatLog.createdAt = Instant.now();
        return chatLog;
    }

    public static AssistantChatLog reconstruct(
            UUID id,
            UUID userId,
            String question,
            String answer,
            List<UUID> matchedDocumentIds,
            List<Float> similarityScores,
            Instant createdAt
    ) {
        AssistantChatLog chatLog = new AssistantChatLog();
        chatLog.id = id;
        chatLog.userId = userId;
        chatLog.question = question;
        chatLog.answer = answer;
        chatLog.matchedDocumentIds = matchedDocumentIds;
        chatLog.similarityScores = similarityScores;
        chatLog.createdAt = createdAt;
        return chatLog;
    }
}
