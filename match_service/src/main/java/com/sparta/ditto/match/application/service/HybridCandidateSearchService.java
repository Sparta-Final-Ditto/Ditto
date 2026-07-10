package com.sparta.ditto.match.application.service;

import com.sparta.ditto.match.application.dto.UserPublicProfileDto;
import com.sparta.ditto.match.infrastructure.feign.UserServiceClient;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 하이브리드 매칭 후보 검색 전략
 *
 * 1순위: HNSW 검색 (synced DB에 데이터 있을 때) → O(log N)
 * 2순위: Feign Fallback (동기화 안 됐을 때) → 기존 방식
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridCandidateSearchService {

    private final VectorSearchService vectorSearchService;
    private final UserServiceClient userServiceClient;

    /**
     * 후보 검색 (HNSW 우선, Feign Fallback)
     *
     * @param neighborhood null이면 위치 필터 미적용
     * @return candidateId → similarity score (유사도 높은 순), 빈 맵이면 Fallback 필요
     */
    public LinkedHashMap<UUID, Float> searchCandidates(
            UUID userId,
            float[] queryVector,
            String genderFilter,
            Integer minAge,
            Integer maxAge,
            String neighborhood,
            int topK
    ) {
        Set<UUID> excludeIds = buildExcludeIds(userId);

        if (vectorSearchService.hasSyncedData()) {
            log.info("[Search] HNSW 검색 사용 userId={}", userId);

            String gender = "NONE".equals(genderFilter) ? null : genderFilter;

            LinkedHashMap<UUID, Float> results;

            if (gender != null || minAge != null || maxAge != null || neighborhood != null) {
                results = vectorSearchService.searchWithAllFilters(
                        userId, queryVector, excludeIds, gender, minAge, maxAge,
                        neighborhood, topK);
            } else {
                results = vectorSearchService.searchSimilarUsers(
                        userId, queryVector, excludeIds, topK);
            }

            if (!results.isEmpty()) {
                log.info("[Search] HNSW 결과 {}명 userId={}", results.size(), userId);
                return results;
            }

            log.warn("[Search] HNSW 결과 없음, Feign Fallback userId={}", userId);
        }

        // Fallback: 빈 맵 반환 → MatchService에서 기존 Feign 로직 실행
        log.info("[Search] Feign Fallback 사용 userId={}", userId);
        return new LinkedHashMap<>();
    }

    /**
     * 팔로잉 + 차단 유저 제외 Set
     */
    public Set<UUID> buildExcludeIds(UUID userId) {
        Set<UUID> excludeIds = new HashSet<>();

        try {
            List<UserPublicProfileDto> followings = userServiceClient
                    .getFollowings(userId).getData();
            if (followings != null) {
                followings.forEach(f -> excludeIds.add(f.id()));
            }
        } catch (Exception e) {
            log.warn("[Search] 팔로잉 조회 실패 userId={}", userId);
        }

        try {
            List<UserPublicProfileDto> blocked = userServiceClient
                    .getBlockedUsers(userId).getData();
            if (blocked != null) {
                blocked.forEach(b -> excludeIds.add(b.id()));
            }
        } catch (Exception e) {
            log.warn("[Search] 차단 유저 조회 실패 userId={}", userId);
        }

        return excludeIds;
    }
}
