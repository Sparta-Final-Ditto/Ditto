package com.sparta.ditto.match.application.service;

import com.sparta.ditto.match.application.port.ExplanationCachePort;
import com.sparta.ditto.match.application.port.LlmPort;
import com.sparta.ditto.match.domain.entity.MatchingExplanation;
import com.sparta.ditto.match.domain.repository.MatchingExplanationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchExplanationService {

    private final LlmPort llmPort;
    private final ExplanationCachePort explanationCachePort;
    private final MatchingExplanationRepository explanationRepository;

    /**
     * RAG 매칭 설명 생성
     * 1. Redis 캐시 확인
     * 2. DB 확인
     * 3. LLM 호출
     * 4. Redis + DB 저장
     */
    public String generateExplanation(UUID userId, UUID matchedUserId,
                                      List<String> commonTags, float score) {

        // 1. Redis 캐시 확인
        Optional<String> cached = explanationCachePort.getExplanation(userId, matchedUserId);
        if (cached.isPresent()) {
            log.info("[Explanation] Redis 캐시 히트 userId={}", userId);
            return cached.get();
        }

        // 2. DB 확인 (이전에 생성된 적 있으면 재사용)
        Optional<MatchingExplanation> saved = explanationRepository
                .findByUserIdAndMatchedUserId(userId, matchedUserId);
        if (saved.isPresent()) {
            String explanation = saved.get().getExplanationText();
            explanationCachePort.saveExplanation(userId, matchedUserId, explanation);
            log.info("[Explanation] DB 히트 userId={}", userId);
            return explanation;
        }

        // 3. LLM 호출 (Fallback 포함)
        String explanation;
        try {
            String prompt = buildPrompt(commonTags, score);
            explanation = llmPort.generate(prompt);
            log.info("[Explanation] LLM 생성 완료 userId={}", userId);
        } catch (Exception e) {
            log.warn("[Explanation] LLM 호출 실패, fallback 사용 userId={} error={}", userId, e.getMessage());
            explanation = buildFallback(commonTags, score);
        }

        // 4. Redis + DB 저장
        explanationCachePort.saveExplanation(userId, matchedUserId, explanation);
        explanationRepository.save(MatchingExplanation.of(userId, matchedUserId, explanation));

        return explanation;
    }

    /**
     * RAG 프롬프트 - 골든셋 예시 포함
     */
    private String buildPrompt(List<String> commonTags, float score) {
        String tagStr = commonTags.isEmpty() ? "공통 관심사 없음" : String.join(", ", commonTags);
        int scorePercent = (int) (score * 100);

        return """
                당신은 SNS 매칭 서비스의 친근한 큐레이터입니다.
                두 사용자의 공통 관심사를 보고 왜 잘 맞는지 한국어로 설명해주세요.
                
                [규칙]
                - 반드시 한국어로 답변
                - 2문장 이내, 100자 이내
                - 따뜻하고 긍정적인 톤
                - 마크다운 금지
                
                [골든셋 예시]
                Q: 공통태그=여행,사진 / 점수=82%%
                A: 여행과 사진을 함께 즐기는 감성이 딱 맞는 두 분이에요! 서로의 여행 사진 이야기로 대화가 끊이지 않을 것 같아요.
                
                Q: 공통태그=독서,카페 / 점수=75%%
                A: 독서와 카페를 좋아하는 취향이 잘 맞는 분들이에요. 조용한 카페에서 책 이야기 나눠보는 건 어떨까요?
                
                Q: 공통태그=운동,헬스 / 점수=90%%
                A: 운동을 향한 열정이 두 분을 이어주었어요! 함께 운동 루틴을 공유하며 서로 동기부여가 될 것 같아요.
                
                Q: 공통태그=요리,맛집 / 점수=78%%
                A: 요리와 맛집 탐방을 좋아하는 두 분의 만남이에요! 함께 새로운 레시피를 나누며 즐거운 시간을 보낼 것 같아요.
                
                [입력]
                공통태그: %s
                매칭점수: %d%%
                
                [답변]
                """.formatted(tagStr, scorePercent);
    }

    /**
     * LLM 실패 시 규칙 기반 Fallback
     */
    private String buildFallback(List<String> commonTags, float score) {
        int scorePercent = (int) (score * 100);
        if (commonTags.isEmpty()) {
            return String.format("매칭 점수 %d%%로 서로 잘 맞는 분들이에요! 새로운 인연을 만들어보세요.", scorePercent);
        }
        String tag1 = commonTags.get(0);
        if (commonTags.size() == 1) {
            return String.format("%s에 대한 공통 관심사로 연결된 두 분이에요. 매칭 점수 %d%%!", tag1, scorePercent);
        }
        return String.format("%s, %s 등 %d개의 공통 관심사를 가진 두 분이에요! 매칭 점수 %d%%로 잘 맞을 것 같아요.",
                tag1, commonTags.get(1), commonTags.size(), scorePercent);
    }
}