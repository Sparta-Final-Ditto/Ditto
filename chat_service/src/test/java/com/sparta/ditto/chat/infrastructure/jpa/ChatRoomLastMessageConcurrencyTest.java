package com.sparta.ditto.chat.infrastructure.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.chat.application.room.ChatRoomMetadataService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * lastMessage 갱신 동시성(비관적 락) 통합 테스트.
 *
 * <p>H2 인메모리는 MVCC 기반이라 실제 PostgreSQL의 {@code SELECT ... FOR UPDATE} 블로킹/lost update
 * 동작을 그대로 재현하지 못한다. 그래서 이 테스트만 Testcontainers로 실제 PostgreSQL에 대고 검증한다.
 *
 * <p>{@code @Transactional(NOT_SUPPORTED)}로 테스트 자체 트랜잭션을 끈다. 그래야 seed/읽기와
 * 각 워커 스레드의 {@code updateLastMessage}가 서로 다른 실제 트랜잭션으로 커밋되어, 스레드 간
 * 동시성이 재현된다(테스트 트랜잭션 안에 갇히지 않는다).
 */
@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ChatRoomMetadataService.class, ChatRoomPersistenceAdapter.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("ChatRoom lastMessage 동시성 통합 테스트 (real PostgreSQL, 비관적 락)")
class ChatRoomLastMessageConcurrencyTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Flyway(V1~V3)가 실제 프로덕션 스키마를 만든다. Hibernate가 그 위에 덮어쓰지 않도록 none.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        // 다중 스레드 워커가 각자 커넥션을 잡으므로 기본 풀(10)보다 넉넉히.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "20");
    }

    @Autowired
    private ChatRoomMetadataService metadataService;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID seedEmptyRoom() {
        UUID roomId = UUID.randomUUID();
        // last_message_* 가 null 인 ACTIVE DIRECT 방. created_at/updated_at/status 는 스키마 기본값 사용.
        jdbc.update(
                "INSERT INTO chat_rooms (id, room_type, created_by) VALUES (?, 'DIRECT', ?)",
                roomId, UUID.randomUUID());
        return roomId;
    }

    @Test
    @DisplayName("빈 방에 더 최신/더 오래된 메시지가 동시에 도착해도, 항상 더 최신 메시지가 lastMessage로 남는다")
    void concurrentOutOfOrderUpdates_newestAlwaysWins() throws Exception {
        // 비관적 락이 없으면: 두 트랜잭션이 모두 빈(=null) lastMessage를 읽고 각자 갱신 → 나중에 커밋한
        // (더 오래된) 메시지가 최신 메시지를 덮어써 lost update가 발생할 수 있다.
        // 락이 있으면: 뒤 트랜잭션이 앞 트랜잭션 커밋 이후 값을 다시 읽어 역전 비교로 걸러낸다.
        int iterations = 30;
        Instant older = Instant.parse("2026-06-20T00:00:00Z");
        Instant newer = Instant.parse("2026-06-20T00:01:00Z");
        String olderMsgId = "00000000-0000-7000-0000-0000000000aa";
        String newerMsgId = "00000000-0000-7000-0000-0000000000bb";

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < iterations; i++) {
                UUID roomId = seedEmptyRoom();
                CyclicBarrier barrier = new CyclicBarrier(2);

                Future<?> newerTask = pool.submit(() -> {
                    barrier.await();
                    metadataService.updateLastMessage(roomId, newerMsgId, newer);
                    return null;
                });
                Future<?> olderTask = pool.submit(() -> {
                    barrier.await();
                    metadataService.updateLastMessage(roomId, olderMsgId, older);
                    return null;
                });
                // 예외(데드락/락 대기 타임아웃 등)가 나면 여기서 던져져 테스트가 실패한다.
                newerTask.get(10, TimeUnit.SECONDS);
                olderTask.get(10, TimeUnit.SECONDS);

                String finalMsgId = jdbc.queryForObject(
                        "SELECT last_message_id FROM chat_rooms WHERE id = ?", String.class, roomId);
                OffsetDateTime finalAt = jdbc.queryForObject(
                        "SELECT last_message_at FROM chat_rooms WHERE id = ?", OffsetDateTime.class, roomId);

                assertThat(finalMsgId)
                        .as("반복 %d회차: 더 최신 메시지가 lastMessage로 남아야 한다", i)
                        .isEqualTo(newerMsgId);
                assertThat(finalAt.toInstant())
                        .as("반복 %d회차: lastMessageAt 도 더 최신 값이어야 한다", i)
                        .isEqualTo(newer);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("여러 메시지가 한 방에 동시에 몰려도, 최종 lastMessage는 가장 최신 메시지다")
    void manyConcurrentUpdates_finalIsTheMaxTimestamp() throws Exception {
        int threadCount = 12;
        UUID roomId = seedEmptyRoom();
        Instant base = Instant.parse("2026-06-20T00:00:00Z");

        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        String maxMsgId = null;
        try {
            for (int i = 0; i < threadCount; i++) {
                int index = i;
                Instant createdAt = base.plusSeconds(index * 60L); // index 클수록 더 최신
                String messageId = String.format("00000000-0000-7000-0000-%012d", index);
                if (index == threadCount - 1) {
                    maxMsgId = messageId;
                }
                futures.add(pool.submit(() -> {
                    barrier.await();
                    metadataService.updateLastMessage(roomId, messageId, createdAt);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        String finalMsgId = jdbc.queryForObject(
                "SELECT last_message_id FROM chat_rooms WHERE id = ?", String.class, roomId);
        assertThat(finalMsgId)
                .as("동시 갱신이 몰려도 최종 값은 가장 최신 메시지여야 한다")
                .isEqualTo(maxMsgId);
    }
}
