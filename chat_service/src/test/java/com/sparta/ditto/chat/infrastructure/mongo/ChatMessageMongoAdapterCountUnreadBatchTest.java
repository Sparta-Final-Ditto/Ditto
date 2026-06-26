package com.sparta.ditto.chat.infrastructure.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatMessageMongoAdapter.countUnreadBatch 라우팅 로직 테스트")
class ChatMessageMongoAdapterCountUnreadBatchTest {

    @Mock
    private ChatMessageMongoRepository chatMessageMongoRepository;
    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ChatMessageMongoAdapter adapter;

    private UUID myUserId;

    @BeforeEach
    void setUp() {
        myUserId = UUID.randomUUID();
    }

    // mongoTemplate.aggregate mock을 테스트마다 직접 설정 — 전역 setup 시 사용 안 한 테스트에서
    // UnnecessaryStubbingException 발생하므로 필요한 테스트에서만 설정한다.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubAggregateReturnsEmpty() {
        AggregationResults mockResults = mock(AggregationResults.class);
        given(mockResults.getMappedResults()).willReturn(List.of());
        given(mongoTemplate.aggregate(
                any(Aggregation.class), anyString(), any(Class.class))
        ).willReturn(mockResults);
    }

    @Nested
    @DisplayName("빈 입력")
    class EmptyInput {

        @Test
        @DisplayName("null 입력이면 쿼리 없이 빈 맵을 반환한다")
        void null_input_returns_empty_without_queries() {
            Map<UUID, Long> result = adapter.countUnreadBatch(null, myUserId);

            assertThat(result).isEmpty();
            verifyNoInteractions(chatMessageMongoRepository, mongoTemplate);
        }

        @Test
        @DisplayName("빈 맵 입력이면 쿼리 없이 빈 맵을 반환한다")
        void empty_map_returns_empty_without_queries() {
            Map<UUID, Long> result = adapter.countUnreadBatch(Map.of(), myUserId);

            assertThat(result).isEmpty();
            verifyNoInteractions(chatMessageMongoRepository, mongoTemplate);
        }
    }

    @Nested
    @DisplayName("noRead 방 (lastReadMessageId 없음)")
    class NoReadRooms {

        @Test
        @DisplayName("null lastRead → 커서 조회 없이 aggregation만 호출한다")
        void null_lastRead_aggregates_without_cursor_fetch() {
            stubAggregateReturnsEmpty();
            UUID roomId = UUID.randomUUID();
            Map<UUID, String> input = new HashMap<>();
            input.put(roomId, null);

            adapter.countUnreadBatch(input, myUserId);

            verify(mongoTemplate).aggregate(any(Aggregation.class), anyString(), any(Class.class));
            verifyNoInteractions(chatMessageMongoRepository);
        }

        @Test
        @DisplayName("빈 문자열 lastRead → 커서 조회 없이 aggregation만 호출한다")
        void blank_lastRead_aggregates_without_cursor_fetch() {
            stubAggregateReturnsEmpty();
            UUID roomId = UUID.randomUUID();

            adapter.countUnreadBatch(Map.of(roomId, ""), myUserId);

            verify(mongoTemplate).aggregate(any(Aggregation.class), anyString(), any(Class.class));
            verifyNoInteractions(chatMessageMongoRepository);
        }

        @Test
        @DisplayName("공백 문자열 lastRead → 커서 조회 없이 aggregation만 호출한다")
        void whitespace_lastRead_aggregates_without_cursor_fetch() {
            stubAggregateReturnsEmpty();
            UUID roomId = UUID.randomUUID();

            adapter.countUnreadBatch(Map.of(roomId, "   "), myUserId);

            verify(mongoTemplate).aggregate(any(Aggregation.class), anyString(), any(Class.class));
            verifyNoInteractions(chatMessageMongoRepository);
        }
    }

    @Nested
    @DisplayName("hasRead 방 (lastReadMessageId 있음)")
    class HasReadRooms {

        @Test
        @DisplayName("커서를 찾은 경우 — 커서 조회 후 aggregation을 1번 호출한다")
        void cursor_found_fetches_cursor_then_aggregates_once() {
            stubAggregateReturnsEmpty();
            UUID roomId = UUID.randomUUID();
            String lastReadId = "msg-cursor-001";

            ChatMessageDocument cursor = mock(ChatMessageDocument.class);
            given(cursor.getMessageId()).willReturn(lastReadId);
            given(cursor.getCreatedAt()).willReturn(Instant.now());
            given(chatMessageMongoRepository.findByMessageIdIn(any()))
                    .willReturn(List.of(cursor));

            adapter.countUnreadBatch(Map.of(roomId, lastReadId), myUserId);

            verify(chatMessageMongoRepository).findByMessageIdIn(
                    argThat(ids -> ids.contains(lastReadId)));
            verify(mongoTemplate, times(1))
                    .aggregate(any(Aggregation.class), anyString(), any(Class.class));
        }

        @Test
        @DisplayName("커서를 찾지 못한 경우 — aggregateUnreadAll(폴백)을 호출한다")
        void cursor_missing_falls_back_to_full_aggregation() {
            stubAggregateReturnsEmpty();
            UUID roomId = UUID.randomUUID();
            String lastReadId = "missing-msg-id";

            given(chatMessageMongoRepository.findByMessageIdIn(any()))
                    .willReturn(List.of());

            adapter.countUnreadBatch(Map.of(roomId, lastReadId), myUserId);

            verify(chatMessageMongoRepository).findByMessageIdIn(any());
            verify(mongoTemplate, times(1))
                    .aggregate(any(Aggregation.class), anyString(), any(Class.class));
        }

        @Test
        @DisplayName("커서 일부만 없을 때 — 찾은 방은 커서 aggregation, 못 찾은 방은 폴백 aggregation")
        void partial_cursor_miss_triggers_both_aggregations() {
            stubAggregateReturnsEmpty();
            UUID foundRoom = UUID.randomUUID();
            UUID missedRoom = UUID.randomUUID();
            String foundId = "msg-found";
            String missedId = "msg-missing";

            ChatMessageDocument cursor = mock(ChatMessageDocument.class);
            given(cursor.getMessageId()).willReturn(foundId);
            given(cursor.getCreatedAt()).willReturn(Instant.now());
            given(chatMessageMongoRepository.findByMessageIdIn(any()))
                    .willReturn(List.of(cursor));

            Map<UUID, String> input = new HashMap<>();
            input.put(foundRoom, foundId);
            input.put(missedRoom, missedId);

            adapter.countUnreadBatch(input, myUserId);

            // 폴백 aggregation(missedRoom) + 커서 aggregation(foundRoom) = 2번
            verify(mongoTemplate, times(2))
                    .aggregate(any(Aggregation.class), anyString(), any(Class.class));
        }
    }

    @Nested
    @DisplayName("noRead + hasRead 혼합")
    class MixedRooms {

        @Test
        @DisplayName("noRead 방과 hasRead 방이 섞이면 aggregation이 2번 호출된다")
        void mixed_rooms_triggers_two_aggregations() {
            stubAggregateReturnsEmpty();
            UUID noReadRoom = UUID.randomUUID();
            UUID hasReadRoom = UUID.randomUUID();
            String lastReadId = "msg-001";

            ChatMessageDocument cursor = mock(ChatMessageDocument.class);
            given(cursor.getMessageId()).willReturn(lastReadId);
            given(cursor.getCreatedAt()).willReturn(Instant.now());
            given(chatMessageMongoRepository.findByMessageIdIn(any()))
                    .willReturn(List.of(cursor));

            Map<UUID, String> input = new HashMap<>();
            input.put(noReadRoom, null);
            input.put(hasReadRoom, lastReadId);

            adapter.countUnreadBatch(input, myUserId);

            // noRead aggregation + hasRead aggregation = 2번
            verify(mongoTemplate, times(2))
                    .aggregate(any(Aggregation.class), anyString(), any(Class.class));
            verify(chatMessageMongoRepository).findByMessageIdIn(any());
        }

        @Test
        @DisplayName("noRead만 있으면 커서 조회 없이 aggregation 1번만 호출된다")
        void only_noRead_rooms_skips_cursor_fetch() {
            stubAggregateReturnsEmpty();
            UUID room1 = UUID.randomUUID();
            UUID room2 = UUID.randomUUID();

            Map<UUID, String> input = new HashMap<>();
            input.put(room1, null);
            input.put(room2, "");

            adapter.countUnreadBatch(input, myUserId);

            verify(mongoTemplate, times(1))
                    .aggregate(any(Aggregation.class), anyString(), any(Class.class));
            verifyNoInteractions(chatMessageMongoRepository);
        }
    }
}
