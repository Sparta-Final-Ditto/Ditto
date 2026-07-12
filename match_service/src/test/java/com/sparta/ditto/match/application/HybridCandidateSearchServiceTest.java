package com.sparta.ditto.match.application;

import com.sparta.ditto.match.application.dto.UserPublicProfileDto;
import com.sparta.ditto.match.application.service.HybridCandidateSearchService;
import com.sparta.ditto.match.application.service.VectorSearchService;
import com.sparta.ditto.match.infrastructure.feign.FeignEnvelope;
import com.sparta.ditto.match.infrastructure.feign.UserServiceClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HybridCandidateSearchServiceTest {

    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private HybridCandidateSearchService hybridCandidateSearchService;

    @Nested
    @DisplayName("searchCandidates()")
    class SearchCandidates {

        @Test
        @DisplayName("동기화 데이터가 없으면 즉시 Feign Fallback으로 빈 결과를 반환한다")
        void noSyncedData_returnsEmptyMap() {
            UUID userId = UUID.randomUUID();
            given(vectorSearchService.hasSyncedData()).willReturn(false);
            given(userServiceClient.getFollowings(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", List.<UserPublicProfileDto>of(), null));
            given(userServiceClient.getBlockedUsers(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", List.<UserPublicProfileDto>of(), null));

            LinkedHashMap<UUID, Float> result = hybridCandidateSearchService.searchCandidates(
                    userId, new float[]{0.1f}, "NONE", null, null, null, 50);

            assertThat(result).isEmpty();
            verify(vectorSearchService, never())
                    .searchSimilarUsers(any(), any(), any(), anyInt());
            verify(vectorSearchService, never()).searchWithAllFilters(
                    any(), any(), any(), any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("필터가 없으면 searchSimilarUsers를 사용한다")
        void syncedDataNoFilters_usesSearchSimilarUsers() {
            UUID userId = UUID.randomUUID();
            UUID candidate = UUID.randomUUID();
            LinkedHashMap<UUID, Float> hnswResults = new LinkedHashMap<>();
            hnswResults.put(candidate, 0.9f);

            given(vectorSearchService.hasSyncedData()).willReturn(true);
            given(userServiceClient.getFollowings(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", List.<UserPublicProfileDto>of(), null));
            given(userServiceClient.getBlockedUsers(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", List.<UserPublicProfileDto>of(), null));
            given(vectorSearchService.searchSimilarUsers(eq(userId), any(), any(), eq(50)))
                    .willReturn(hnswResults);

            LinkedHashMap<UUID, Float> result = hybridCandidateSearchService.searchCandidates(
                    userId, new float[]{0.1f}, "NONE", null, null, null, 50);

            assertThat(result).containsEntry(candidate, 0.9f);
            verify(vectorSearchService, never()).searchWithAllFilters(
                    any(), any(), any(), any(), any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("성별/나이/동네 필터가 있으면 searchWithAllFilters를 사용한다")
        void syncedDataWithFilters_usesSearchWithAllFilters() {
            UUID userId = UUID.randomUUID();
            UUID candidate = UUID.randomUUID();
            LinkedHashMap<UUID, Float> hnswResults = new LinkedHashMap<>();
            hnswResults.put(candidate, 0.8f);

            given(vectorSearchService.hasSyncedData()).willReturn(true);
            given(userServiceClient.getFollowings(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", List.<UserPublicProfileDto>of(), null));
            given(userServiceClient.getBlockedUsers(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", List.<UserPublicProfileDto>of(), null));
            given(vectorSearchService.searchWithAllFilters(
                    eq(userId), any(), any(), eq("MALE"), eq(20), eq(30), eq("서울 성동구"), eq(50)))
                    .willReturn(hnswResults);

            LinkedHashMap<UUID, Float> result = hybridCandidateSearchService.searchCandidates(
                    userId, new float[]{0.1f}, "MALE", 20, 30, "서울 성동구", 50);

            assertThat(result).containsEntry(candidate, 0.8f);
            verify(vectorSearchService, never())
                    .searchSimilarUsers(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("HNSW 검색 결과가 비어있으면 Feign Fallback으로 빈 맵을 반환한다")
        void syncedDataButNoResults_fallsBackToEmptyMap() {
            UUID userId = UUID.randomUUID();

            given(vectorSearchService.hasSyncedData()).willReturn(true);
            given(userServiceClient.getFollowings(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", List.<UserPublicProfileDto>of(), null));
            given(userServiceClient.getBlockedUsers(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", List.<UserPublicProfileDto>of(), null));
            given(vectorSearchService.searchSimilarUsers(eq(userId), any(), any(), eq(50)))
                    .willReturn(new LinkedHashMap<>());

            LinkedHashMap<UUID, Float> result = hybridCandidateSearchService.searchCandidates(
                    userId, new float[]{0.1f}, "NONE", null, null, null, 50);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("buildExcludeIds()")
    class BuildExcludeIds {

        @Test
        @DisplayName("팔로잉과 차단 유저를 모두 합쳐 제외 목록을 만든다")
        void combinesFollowingsAndBlocked() {
            UUID userId = UUID.randomUUID();
            UUID following = UUID.randomUUID();
            UUID blocked = UUID.randomUUID();

            given(userServiceClient.getFollowings(userId)).willReturn(
                    new FeignEnvelope<>(200, null, "SUCCESS", List.of(new UserPublicProfileDto(
                            following, "nick", null, null)), null));
            given(userServiceClient.getBlockedUsers(userId)).willReturn(
                    new FeignEnvelope<>(200, null, "SUCCESS", List.of(new UserPublicProfileDto(
                            blocked, "nick2", null, null)), null));

            Set<UUID> result = hybridCandidateSearchService.buildExcludeIds(userId);

            assertThat(result).containsExactlyInAnyOrder(following, blocked);
        }

        @Test
        @DisplayName("팔로잉 조회가 실패해도 차단 유저 목록은 반영한다")
        void followingsFail_stillIncludesBlocked() {
            UUID userId = UUID.randomUUID();
            UUID blocked = UUID.randomUUID();

            given(userServiceClient.getFollowings(userId))
                    .willThrow(new RuntimeException("user-service down"));
            given(userServiceClient.getBlockedUsers(userId)).willReturn(
                    new FeignEnvelope<>(200, null, "SUCCESS", List.of(new UserPublicProfileDto(
                            blocked, "nick", null, null)), null));

            Set<UUID> result = hybridCandidateSearchService.buildExcludeIds(userId);

            assertThat(result).containsExactly(blocked);
        }

        @Test
        @DisplayName("차단 유저 조회가 실패해도 팔로잉 목록은 반영한다")
        void blockedFail_stillIncludesFollowings() {
            UUID userId = UUID.randomUUID();
            UUID following = UUID.randomUUID();

            given(userServiceClient.getFollowings(userId)).willReturn(
                    new FeignEnvelope<>(200, null, "SUCCESS", List.of(new UserPublicProfileDto(
                            following, "nick", null, null)), null));
            given(userServiceClient.getBlockedUsers(userId))
                    .willThrow(new RuntimeException("user-service down"));

            Set<UUID> result = hybridCandidateSearchService.buildExcludeIds(userId);

            assertThat(result).containsExactly(following);
        }

        @Test
        @DisplayName("팔로잉/차단 응답 데이터가 null이면 빈 목록으로 처리한다")
        void nullData_returnsEmptySet() {
            UUID userId = UUID.randomUUID();

            given(userServiceClient.getFollowings(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", null, null));
            given(userServiceClient.getBlockedUsers(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", null, null));

            Set<UUID> result = hybridCandidateSearchService.buildExcludeIds(userId);

            assertThat(result).isEmpty();
        }
    }
}
