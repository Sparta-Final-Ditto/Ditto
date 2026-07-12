package com.sparta.ditto.match.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.match.application.dto.ActiveUserIdsDto;
import com.sparta.ditto.match.application.dto.ProfileBatchRequestDto;
import com.sparta.ditto.match.application.dto.ProfileBatchResponseDto;
import com.sparta.ditto.match.application.dto.UserNeighborhoodDto;
import com.sparta.ditto.match.application.dto.UserProfileEmbeddingDto;
import com.sparta.ditto.match.domain.entity.SyncedProfileEmbedding;
import com.sparta.ditto.match.domain.repository.SyncedProfileEmbeddingRepository;
import com.sparta.ditto.match.infrastructure.feign.EmbeddingServiceClient;
import com.sparta.ditto.match.infrastructure.feign.UserServiceClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * embedding_service → match_service DB 동기화 서비스 (CQRS 패턴)
 *
 * 동기화 방식 3가지:
 * 1. Kafka 개별 이벤트 (PROFILE_EMBEDDING_UPDATED) - 데일리 1000명 이하
 * 2. Kafka 벌크 이벤트 (PROFILE_EMBEDDING_BULK_COMPLETED) - 데일리 1000명 초과 + 월배치
 * 3. 스케줄러 배치 (30분마다) - Kafka 실패 시 Fallback
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingSyncService {

    private final EmbeddingServiceClient embeddingServiceClient;
    private final UserServiceClient userServiceClient;
    private final SyncedProfileEmbeddingRepository syncedRepository;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 100;

    // ── Kafka Consumer: 개별 벡터 동기화 ──────────────────────────

    /**
     * 데일리 배치 (1000명 이하) - 개별 벡터 이벤트 수신
     * 토픽: profile-embedding-updated
     */
    @KafkaListener(topics = "profile-embedding-updated", groupId = "match-sync-group")
    @Transactional
    public void onProfileEmbeddingUpdated(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.get("eventType").asText();

            if (!"PROFILE_EMBEDDING_UPDATED".equals(eventType)) {
                return;
            }

            JsonNode payload = root.get("payload");
            UUID userId = UUID.fromString(payload.get("userId").asText());
            boolean active = payload.get("active").asBoolean();

            // 벡터 파싱
            JsonNode vectorNode = payload.get("profileVector");
            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = (float) vectorNode.get(i).asDouble();
            }

            // 동네 정보 조회
            String neighborhood = fetchNeighborhood(userId);

            upsertSyncedEmbedding(userId, vector, null, null, neighborhood, active);
            log.info("[Sync] 개별 동기화 완료 userId={}", userId);

        } catch (Exception e) {
            log.error("[Sync] 개별 동기화 실패: {}", e.getMessage());
        }
    }

    // ── Kafka Consumer: 벌크 동기화 신호 ──────────────────────────

    /**
     * 데일리 배치 (1000명 초과) + 월배치 - 벌크 완료 신호 수신
     * 토픽: profile-embedding-bulk-completed
     * 벡터 데이터 없이 신호만 → 배치 API로 전체 재적재
     */
    @KafkaListener(topics = "profile-embedding-bulk-completed", groupId = "match-sync-group")
    @Transactional
    public void onBulkCompleted(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.get("eventType").asText();

            if (!"PROFILE_EMBEDDING_BULK_COMPLETED".equals(eventType)) {
                return;
            }

            JsonNode payload = root.get("payload");
            String batchType = payload.get("batchType").asText();
            int totalUpdated = payload.get("totalUpdated").asInt();

            log.info("[Sync] 벌크 완료 신호 수신 batchType={} updated={}", batchType, totalUpdated);

            // 전체 재적재
            syncAll();

        } catch (Exception e) {
            log.error("[Sync] 벌크 동기화 실패: {}", e.getMessage());
        }
    }

    // ── 배치 동기화 (Fallback) ──────────────────────────

    /**
     * 30분마다 실행 - Kafka 실패 시 Fallback
     */
    @Scheduled(fixedRate = 1800000)
    @Transactional
    public void syncAll() {
        log.info("[Sync] 전체 동기화 시작");

        try {
            ActiveUserIdsDto activeIds = embeddingServiceClient
                    .getActiveUserIds().data();

            if (activeIds == null || activeIds.userIds().isEmpty()) {
                log.info("[Sync] 동기화할 active 유저 없음");
                return;
            }

            List<UUID> allUserIds = activeIds.userIds();
            int totalSynced = 0;

            for (int i = 0; i < allUserIds.size(); i += BATCH_SIZE) {
                List<UUID> batch = allUserIds.subList(
                        i, Math.min(i + BATCH_SIZE, allUserIds.size()));

                try {
                    ProfileBatchResponseDto response = embeddingServiceClient
                            .getProfilesBatch(new ProfileBatchRequestDto(batch)).data();

                    for (UserProfileEmbeddingDto profile : response.profiles()) {
                        float[] vector = profile.todayVector() != null
                                ? profile.todayVector()
                                : profile.profileVector();

                        if (vector == null) {
                            continue;
                        }

                        String neighborhood = fetchNeighborhood(profile.userId());
                        upsertSyncedEmbedding(
                                profile.userId(), vector, null, null,
                                neighborhood, profile.active());
                        totalSynced++;
                    }
                } catch (Exception e) {
                    log.warn("[Sync] 배치 실패 batch={}-{}: {}",
                            i, Math.min(i + BATCH_SIZE, allUserIds.size()), e.getMessage());
                }
            }

            log.info("[Sync] 전체 동기화 완료 synced={}/{}", totalSynced, allUserIds.size());

        } catch (Exception e) {
            log.error("[Sync] 전체 동기화 실패: {}", e.getMessage());
        }
    }

    // ── 공통 upsert ──────────────────────────

    private void upsertSyncedEmbedding(
            UUID userId, float[] vector, String gender, LocalDate birthdate,
            String neighborhood, boolean active
    ) {
        Optional<SyncedProfileEmbedding> existing = syncedRepository.findById(userId);

        if (existing.isPresent()) {
            existing.get().updateVector(vector, gender, birthdate, neighborhood, active);
        } else {
            syncedRepository.save(
                    SyncedProfileEmbedding.of(
                            userId, vector, gender, birthdate, neighborhood, active));
        }
    }

    private String fetchNeighborhood(UUID userId) {
        try {
            UserNeighborhoodDto profile = userServiceClient.getMyProfile(userId).data();
            return profile != null ? profile.neighborhood() : null;
        } catch (Exception e) {
            log.warn("[Sync] 동네 정보 조회 실패 userId={}", userId);
            return null;
        }
    }
}
