package com.sparta.ditto.notification.infrastructure.sse;

import com.sparta.ditto.notification.application.NotificationRecorder;
import com.sparta.ditto.notification.application.dto.NotificationPushPayload;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import com.sparta.ditto.notification.application.port.NotificationPushPort;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.infrastructure.config.AsyncConfig;
import com.sparta.ditto.notification.infrastructure.messaging.MetaDataAdapter;
import com.sparta.ditto.notification.infrastructure.persistence.NotificationJpaRepository;
import com.sparta.ditto.notification.infrastructure.persistence.NotificationRepositoryImpl;
import com.sparta.ditto.notification.support.AbstractPostgresContainerTest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationRepositoryImpl.class, NotificationRecorder.class, NotificationPushListener.class,
        MetaDataAdapter.class, AsyncConfig.class,
        NotificationPushAfterCommitIntegrationTest.RecordingPushConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("저장 커밋 후 AFTER_COMMIT push 호출 + 전송 실패 시 저장 격리")
class NotificationPushAfterCommitIntegrationTest extends AbstractPostgresContainerTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAwareImpl() {
            return Optional::empty;
        }
    }

    /** NotificationPushPort를 기록용 테스트 더블로 대체(@MockitoBean 미사용 → 컨텍스트 캐싱 보존). */
    @TestConfiguration
    static class RecordingPushConfig {
        @Bean
        RecordingPushPort recordingPushPort() {
            return new RecordingPushPort();
        }
    }

    static class RecordingPushPort implements NotificationPushPort {
        final List<NotificationPushPayload> pushed = new CopyOnWriteArrayList<>();
        volatile boolean throwOnPush = false;

        @Override
        public void push(UUID receiverId, NotificationPushPayload payload) {
            pushed.add(payload);
            if (throwOnPush) {
                throw new RuntimeException("push failed");
            }
        }
    }

    @Autowired
    private NotificationRecorder recorder;

    @Autowired
    private NotificationJpaRepository jpaRepository;

    @Autowired
    private RecordingPushPort recordingPushPort;

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        jpaRepository.deleteAll();
        recordingPushPort.pushed.clear();
        recordingPushPort.throwOnPush = false;
    }

    @Test
    @DisplayName("Recorder 저장이 커밋되면 AFTER_COMMIT 이후 NotificationPushPort.push가 호출된다")
    void afterCommit_invokesPush() {
        PostNotificationCommand cmd = PostNotificationCommand.of(
                "POST_LIKED", "like_int_1", "post_int_1", ACTOR_ID, "행위자", OWNER_ID);

        recorder.recordPost(cmd);

        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(recordingPushPort.pushed).hasSize(1));

        NotificationPushPayload payload = recordingPushPort.pushed.get(0);
        assertThat(payload.type()).isEqualTo(NotificationType.LIKE);
        assertThat(payload.isRead()).isFalse();
        assertThat(payload.metaData()).contains("post_int_1");
        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("push가 예외를 던져도 알림 DB 저장은 유지된다 (전송 실패 격리)")
    void pushFailure_doesNotAffectPersistedNotification() {
        recordingPushPort.throwOnPush = true;
        PostNotificationCommand cmd = PostNotificationCommand.of(
                "POST_COMMENTED", "cmt_int_1", "post_int_2", ACTOR_ID, "행위자", OWNER_ID);

        recorder.recordPost(cmd);

        // push는 시도되지만(격리) 예외는 알림 저장에 영향을 주지 않는다
        Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(recordingPushPort.pushed).hasSize(1));
        assertThat(jpaRepository.count()).isEqualTo(1L);
    }
}
