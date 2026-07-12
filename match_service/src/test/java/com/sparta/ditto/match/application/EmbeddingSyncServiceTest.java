package com.sparta.ditto.match.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.match.application.dto.ActiveUserIdsDto;
import com.sparta.ditto.match.application.dto.ProfileBatchResponseDto;
import com.sparta.ditto.match.application.dto.UserNeighborhoodDto;
import com.sparta.ditto.match.application.dto.UserProfileEmbeddingDto;
import com.sparta.ditto.match.application.service.EmbeddingSyncService;
import com.sparta.ditto.match.domain.entity.SyncedProfileEmbedding;
import com.sparta.ditto.match.domain.repository.SyncedProfileEmbeddingRepository;
import com.sparta.ditto.match.infrastructure.feign.EmbeddingServiceClient;
import com.sparta.ditto.match.infrastructure.feign.FeignEnvelope;
import com.sparta.ditto.match.infrastructure.feign.UserServiceClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmbeddingSyncServiceTest {

    @Mock
    private EmbeddingServiceClient embeddingServiceClient;
    @Mock
    private UserServiceClient userServiceClient;
    @Mock
    private SyncedProfileEmbeddingRepository syncedRepository;

    private EmbeddingSyncService embeddingSyncService;

    @BeforeEach
    void setUp() {
        embeddingSyncService = new EmbeddingSyncService(
                embeddingServiceClient, userServiceClient, syncedRepository, new ObjectMapper());
    }

    @Nested
    @DisplayName("onProfileEmbeddingUpdated()")
    class OnProfileEmbeddingUpdated {

        @Test
        @DisplayName("성공 - 신규 유저는 동네 정보를 조회해 저장한다")
        void success_newUser_savesWithNeighborhood() {
            UUID userId = UUID.randomUUID();
            String message = "{"
                    + "\"eventType\":\"PROFILE_EMBEDDING_UPDATED\","
                    + "\"payload\":{"
                    + "\"userId\":\"" + userId + "\","
                    + "\"active\":true,"
                    + "\"profileVector\":[0.1,0.2,0.3]"
                    + "}}";

            given(syncedRepository.findById(userId)).willReturn(Optional.empty());
            given(userServiceClient.getMyProfile(userId))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", new UserNeighborhoodDto(userId, "서울 성동구"), null));

            embeddingSyncService.onProfileEmbeddingUpdated(message);

            verify(syncedRepository).save(any(SyncedProfileEmbedding.class));
        }

        @Test
        @DisplayName("성공 - 기존 유저는 벡터를 갱신하고, 동네 조회 실패 시 null로 반영한다")
        void success_existingUser_updatesVectorAndNeighborhoodFetchFails() {
            UUID userId = UUID.randomUUID();
            String message = "{"
                    + "\"eventType\":\"PROFILE_EMBEDDING_UPDATED\","
                    + "\"payload\":{"
                    + "\"userId\":\"" + userId + "\","
                    + "\"active\":false,"
                    + "\"profileVector\":[0.4,0.5]"
                    + "}}";
            SyncedProfileEmbedding existing = SyncedProfileEmbedding.of(
                    userId, new float[]{0.1f, 0.2f}, null, null, null, true);

            given(syncedRepository.findById(userId)).willReturn(Optional.of(existing));
            given(userServiceClient.getMyProfile(userId))
                    .willThrow(new RuntimeException("user-service down"));

            embeddingSyncService.onProfileEmbeddingUpdated(message);

            verify(syncedRepository, never()).save(any());
            assertThat(existing.getActive()).isFalse();
            assertThat(existing.getNeighborhood()).isNull();
        }

        @Test
        @DisplayName("eventType이 다르면 아무 동작도 하지 않는다")
        void eventTypeMismatch_doesNothing() {
            String message = "{\"eventType\":\"SOMETHING_ELSE\",\"payload\":{}}";

            embeddingSyncService.onProfileEmbeddingUpdated(message);

            verify(syncedRepository, never()).findById(any());
            verify(syncedRepository, never()).save(any());
        }

        @Test
        @DisplayName("잘못된 JSON이면 예외를 삼키고 정상 종료된다")
        void malformedJson_doesNotThrow() {
            assertThatCode(() -> embeddingSyncService.onProfileEmbeddingUpdated("not-a-json"))
                    .doesNotThrowAnyException();

            verify(syncedRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("onBulkCompleted()")
    class OnBulkCompleted {

        @Test
        @DisplayName("성공 - 벌크 완료 이벤트 수신 시 전체 재동기화를 수행한다")
        void success_triggersFullSync() {
            String message = "{"
                    + "\"eventType\":\"PROFILE_EMBEDDING_BULK_COMPLETED\","
                    + "\"payload\":{\"batchType\":\"DAILY\",\"totalUpdated\":10}}";

            given(embeddingServiceClient.getActiveUserIds())
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", new ActiveUserIdsDto(List.of(), 0), null));

            embeddingSyncService.onBulkCompleted(message);

            verify(embeddingServiceClient).getActiveUserIds();
        }

        @Test
        @DisplayName("eventType이 다르면 재동기화를 수행하지 않는다")
        void eventTypeMismatch_doesNotSync() {
            String message = "{\"eventType\":\"SOMETHING_ELSE\",\"payload\":{}}";

            embeddingSyncService.onBulkCompleted(message);

            verify(embeddingServiceClient, never()).getActiveUserIds();
        }

        @Test
        @DisplayName("잘못된 JSON이면 예외를 삼키고 정상 종료된다")
        void malformedJson_doesNotThrow() {
            assertThatCode(() -> embeddingSyncService.onBulkCompleted("not-a-json"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("syncAll()")
    class SyncAll {

        @Test
        @DisplayName("active 유저 목록이 비어있으면 동기화 없이 종료된다")
        void emptyActiveUsers_doesNothing() {
            given(embeddingServiceClient.getActiveUserIds())
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", new ActiveUserIdsDto(List.of(), 0), null));

            embeddingSyncService.syncAll();

            verify(embeddingServiceClient, never()).getProfilesBatch(any());
        }

        @Test
        @DisplayName("active 유저 응답이 null이면 동기화 없이 종료된다")
        void nullActiveUsers_doesNothing() {
            given(embeddingServiceClient.getActiveUserIds())
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", null, null));

            embeddingSyncService.syncAll();

            verify(embeddingServiceClient, never()).getProfilesBatch(any());
        }

        @Test
        @DisplayName("todayVector가 있으면 todayVector로, 둘 다 없으면 건너뛰고 동기화한다")
        void mixedProfiles_syncsAvailableVectorsAndSkipsNull() {
            UUID userId = UUID.randomUUID();
            UUID todayVectorUser = UUID.randomUUID();
            UUID noVectorUser = UUID.randomUUID();

            ActiveUserIdsDto activeIds = new ActiveUserIdsDto(
                    List.of(userId, todayVectorUser, noVectorUser), 3);

            UserProfileEmbeddingDto withProfileVector =
                    new UserProfileEmbeddingDto(userId, new float[]{0.1f, 0.2f}, null, true, 1);
            UserProfileEmbeddingDto withTodayVector = new UserProfileEmbeddingDto(
                    todayVectorUser, new float[]{0.1f, 0.2f}, new float[]{0.9f, 0.8f}, true, 5);
            UserProfileEmbeddingDto withoutVector =
                    new UserProfileEmbeddingDto(noVectorUser, null, null, false, 0);

            ProfileBatchResponseDto batchResponse = new ProfileBatchResponseDto(
                    List.of(withProfileVector, withTodayVector, withoutVector));

            given(embeddingServiceClient.getActiveUserIds())
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", activeIds, null));
            given(embeddingServiceClient.getProfilesBatch(any()))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", batchResponse, null));
            given(syncedRepository.findById(any())).willReturn(Optional.empty());
            given(userServiceClient.getMyProfile(any()))
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", new UserNeighborhoodDto(userId, "동네"), null));

            embeddingSyncService.syncAll();

            verify(syncedRepository, times(2)).save(any(SyncedProfileEmbedding.class));
        }

        @Test
        @DisplayName("배치 조회가 실패해도 예외를 삼키고 정상 종료된다")
        void batchFetchFails_doesNotThrow() {
            UUID userId = UUID.randomUUID();
            ActiveUserIdsDto activeIds = new ActiveUserIdsDto(List.of(userId), 1);

            given(embeddingServiceClient.getActiveUserIds())
                    .willReturn(new FeignEnvelope<>(200, null, "SUCCESS", activeIds, null));
            given(embeddingServiceClient.getProfilesBatch(any()))
                    .willThrow(new RuntimeException("batch failed"));

            assertThatCode(() -> embeddingSyncService.syncAll()).doesNotThrowAnyException();

            verify(syncedRepository, never()).save(any());
        }

        @Test
        @DisplayName("active 유저 조회 자체가 실패해도 예외를 삼키고 정상 종료된다")
        void activeUserFetchFails_doesNotThrow() {
            given(embeddingServiceClient.getActiveUserIds())
                    .willThrow(new RuntimeException("connection refused"));

            assertThatCode(() -> embeddingSyncService.syncAll()).doesNotThrowAnyException();
        }
    }
}
