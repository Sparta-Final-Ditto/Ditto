package com.sparta.ditto.match.application;

import com.sparta.ditto.match.application.service.VectorSearchService;
import com.sparta.ditto.match.domain.repository.SyncedProfileEmbeddingRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VectorSearchServiceTest {

    @Mock
    private SyncedProfileEmbeddingRepository syncedRepository;

    @InjectMocks
    private VectorSearchService vectorSearchService;

    @Nested
    @DisplayName("searchSimilarUsers()")
    class SearchSimilarUsers {

        @Test
        @DisplayName("결과가 있으면 유사도 순서를 유지한 채 Map으로 변환한다")
        void hasResults_preservesOrder() {
            UUID userId = UUID.randomUUID();
            UUID candidate1 = UUID.randomUUID();
            UUID candidate2 = UUID.randomUUID();
            List<Object[]> rows = List.of(
                    new Object[]{candidate1, 0.9f},
                    new Object[]{candidate2, 0.7f});

            given(syncedRepository.findSimilarUsers(eq(userId), any(), any(), anyInt()))
                    .willReturn(rows);

            LinkedHashMap<UUID, Float> result = vectorSearchService.searchSimilarUsers(
                    userId, new float[]{0.1f, 0.2f}, Set.of(), 10);

            assertThat(result.keySet()).containsExactly(candidate1, candidate2);
            assertThat(result.get(candidate1)).isEqualTo(0.9f);
            assertThat(result.get(candidate2)).isEqualTo(0.7f);
        }

        @Test
        @DisplayName("excludeIds가 비어있으면 더미 UUID로 대체해 조회한다")
        void emptyExcludeIds_usesDummyUuid() {
            UUID userId = UUID.randomUUID();

            given(syncedRepository.findSimilarUsers(eq(userId), any(), any(), anyInt()))
                    .willReturn(List.of());

            vectorSearchService.searchSimilarUsers(userId, new float[]{0.1f}, Set.of(), 5);

            ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
            verify(syncedRepository)
                    .findSimilarUsers(eq(userId), any(), captor.capture(), eq(5));
            assertThat(captor.getValue())
                    .containsExactly(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        }

        @Test
        @DisplayName("excludeIds가 있으면 그대로 전달한다")
        void nonEmptyExcludeIds_passesThrough() {
            UUID userId = UUID.randomUUID();
            UUID excluded = UUID.randomUUID();

            given(syncedRepository.findSimilarUsers(eq(userId), any(), any(), anyInt()))
                    .willReturn(List.of());

            vectorSearchService.searchSimilarUsers(
                    userId, new float[]{0.1f}, Set.of(excluded), 5);

            ArgumentCaptor<List<UUID>> captor = ArgumentCaptor.forClass(List.class);
            verify(syncedRepository)
                    .findSimilarUsers(eq(userId), any(), captor.capture(), eq(5));
            assertThat(captor.getValue()).containsExactly(excluded);
        }

        @Test
        @DisplayName("쿼리 벡터를 pgvector 문자열 형식으로 변환해 전달한다")
        void convertsVectorToPgvectorString() {
            UUID userId = UUID.randomUUID();
            given(syncedRepository.findSimilarUsers(eq(userId), any(), any(), anyInt()))
                    .willReturn(List.of());

            vectorSearchService.searchSimilarUsers(
                    userId, new float[]{0.1f, 0.2f, 0.3f}, Set.of(), 5);

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(syncedRepository)
                    .findSimilarUsers(eq(userId), captor.capture(), any(), eq(5));
            assertThat(captor.getValue()).isEqualTo("[0.1,0.2,0.3]");
        }
    }

    @Nested
    @DisplayName("searchWithGenderFilter()")
    class SearchWithGenderFilter {

        @Test
        @DisplayName("성별 필터를 포함해 검색한다")
        void searchesWithGenderFilter() {
            UUID userId = UUID.randomUUID();
            UUID candidate = UUID.randomUUID();

            given(syncedRepository.findSimilarUsersWithGenderFilter(
                    eq(userId), any(), any(), eq("MALE"), anyInt()))
                    .willReturn(List.of(new Object[][]{{candidate, 0.5f}}));

            LinkedHashMap<UUID, Float> result = vectorSearchService.searchWithGenderFilter(
                    userId, new float[]{0.1f}, Set.of(), "MALE", 10);

            assertThat(result).containsEntry(candidate, 0.5f);
        }
    }

    @Nested
    @DisplayName("searchWithAllFilters()")
    class SearchWithAllFilters {

        @Test
        @DisplayName("성별/나이/동네 필터를 모두 포함해 검색한다")
        void searchesWithAllFilters() {
            UUID userId = UUID.randomUUID();
            UUID candidate = UUID.randomUUID();

            given(syncedRepository.findSimilarUsersWithFilters(
                    eq(userId), any(), any(), eq("FEMALE"), eq(20), eq(30),
                    eq("서울 성동구"), anyInt()))
                    .willReturn(List.of(new Object[][]{{candidate, 0.8f}}));

            LinkedHashMap<UUID, Float> result = vectorSearchService.searchWithAllFilters(
                    userId, new float[]{0.1f}, Set.of(), "FEMALE", 20, 30, "서울 성동구", 10);

            assertThat(result).containsEntry(candidate, 0.8f);
        }
    }

    @Nested
    @DisplayName("hasSyncedData()")
    class HasSyncedData {

        @Test
        @DisplayName("동기화된 활성 유저가 있으면 true를 반환한다")
        void hasActiveUsers_returnsTrue() {
            given(syncedRepository.countByActiveTrue()).willReturn(5L);

            assertThat(vectorSearchService.hasSyncedData()).isTrue();
        }

        @Test
        @DisplayName("동기화된 활성 유저가 없으면 false를 반환한다")
        void noActiveUsers_returnsFalse() {
            given(syncedRepository.countByActiveTrue()).willReturn(0L);

            assertThat(vectorSearchService.hasSyncedData()).isFalse();
        }
    }
}
