package com.sparta.ditto.notification.infrastructure.persistence;

import com.sparta.ditto.notification.application.NotificationEventHandler;
import com.sparta.ditto.notification.application.dto.ChatNotificationCommand;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({NotificationRepositoryImpl.class, NotificationEventHandler.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("동일 이벤트 중복 수신 멱등성 — UNIQUE 제약 존재 + 핸들러 레벨 skip")
class NotificationIdempotencyTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {
        @Bean
        AuditorAware<UUID> auditorAwareImpl() {
            return Optional::empty;
        }
    }

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private NotificationEventHandler handler;

    @Autowired
    private NotificationJpaRepository jpaRepository;

    private static final UUID OWNER_ID   = UUID.randomUUID();
    private static final UUID ACTOR_A    = UUID.randomUUID();
    private static final UUID ACTOR_B    = UUID.randomUUID();
    private static final UUID SENDER_ID  = UUID.randomUUID();
    private static final UUID RECEIVER_1 = UUID.randomUUID();
    private static final UUID RECEIVER_2 = UUID.randomUUID();

    private static final String POST_ID      = "post_idm_001";
    private static final String LIKE_ID_1    = "like_idm_001";
    private static final String LIKE_ID_2    = "like_idm_002";
    private static final String COMMENT_ID_1 = "cmt_idm_001";
    private static final String MSG_ID_1     = "msg_idm_001";
    private static final String ROOM_ID      = "room_idm_001";

    @AfterEach
    void cleanup() {
        jpaRepository.deleteAll();
    }

    // ── 1. DB UNIQUE 제약 존재 확인 (type 3종) ─────────────────────────────────
    // actor_id가 달라도 (type, target_id, receiver_id)가 같으면 중복으로 간주한다.

    @Test
    @DisplayName("LIKE: (type=LIKE, target_id=likeId, receiver_id=ownerId) 동일 2회 직접 저장 → DataIntegrityViolationException")
    void uniqueConstraint_LIKE_duplicateDirectSave_throwsDataIntegrityViolationException() {
        Notification n1 = Notification.create(OWNER_ID, ACTOR_A,
                NotificationType.LIKE, TargetType.LIKE, LIKE_ID_1, "msg", null);
        Notification n2 = Notification.create(OWNER_ID, ACTOR_B,   // actorId만 다름, 나머지 동일
                NotificationType.LIKE, TargetType.LIKE, LIKE_ID_1, "msg", null);

        jpaRepository.saveAndFlush(n1);

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(n2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("COMMENT: (type=COMMENT, target_id=commentId, receiver_id=ownerId) 동일 2회 직접 저장 → DataIntegrityViolationException")
    void uniqueConstraint_COMMENT_duplicateDirectSave_throwsDataIntegrityViolationException() {
        Notification n1 = Notification.create(OWNER_ID, ACTOR_A,
                NotificationType.COMMENT, TargetType.COMMENT, COMMENT_ID_1, "msg", null);
        Notification n2 = Notification.create(OWNER_ID, ACTOR_B,
                NotificationType.COMMENT, TargetType.COMMENT, COMMENT_ID_1, "msg", null);

        jpaRepository.saveAndFlush(n1);

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(n2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHAT_MESSAGE: (type=CHAT_MESSAGE, target_id=messageId, receiver_id) 동일 2회 직접 저장 → DataIntegrityViolationException")
    void uniqueConstraint_CHAT_MESSAGE_duplicateDirectSave_throwsDataIntegrityViolationException() {
        Notification n1 = Notification.create(RECEIVER_1, SENDER_ID,
                NotificationType.CHAT_MESSAGE, TargetType.CHAT_MESSAGE, MSG_ID_1, "preview", null);
        Notification n2 = Notification.create(RECEIVER_1, SENDER_ID,
                NotificationType.CHAT_MESSAGE, TargetType.CHAT_MESSAGE, MSG_ID_1, "preview", null);

        jpaRepository.saveAndFlush(n1);

        assertThatThrownBy(() -> jpaRepository.saveAndFlush(n2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── 2. 핸들러 레벨 멱등 처리: 중복 위반은 catch 후 skip — Consumer에 전파 없음 ──
    // GREEN 미구현 → 현재 두 번째 호출이 DataIntegrityViolationException을 전파 → RED FAIL

    @Test
    @DisplayName("POST_LIKED 동일 이벤트 2회 수신: 두 번째는 예외 없이 skip되고 총 1건만 저장된다")
    void handlePostEvent_likeDuplicate_secondCallSkippedWithNoExceptionAndOnlyOneRowSaved() {
        PostNotificationCommand cmd = PostNotificationCommand.of(
                "POST_LIKED", LIKE_ID_1, POST_ID, ACTOR_A, "닉네임A", OWNER_ID);

        handler.handlePostEvent(cmd);

        // 두 번째 동일 이벤트 → DataIntegrityViolationException이 전파되지 않아야 한다
        assertThatCode(() -> handler.handlePostEvent(cmd))
                .doesNotThrowAnyException();

        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("POST_COMMENTED 동일 이벤트 2회 수신: 두 번째는 예외 없이 skip되고 총 1건만 저장된다")
    void handlePostEvent_commentDuplicate_secondCallSkippedWithNoExceptionAndOnlyOneRowSaved() {
        PostNotificationCommand cmd = PostNotificationCommand.of(
                "POST_COMMENTED", COMMENT_ID_1, POST_ID, ACTOR_A, "닉네임A", OWNER_ID);

        handler.handlePostEvent(cmd);

        assertThatCode(() -> handler.handlePostEvent(cmd))
                .doesNotThrowAnyException();

        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("CHAT 동일 messageId + 동일 receiverId 2회 수신: skip되어 예외 없이 정상 종료하고 총 1건만 저장된다")
    void handleChatMessage_duplicatePerReceiver_secondCallSkippedWithNoExceptionAndOnlyOneRowSaved() {
        ChatNotificationCommand cmd = ChatNotificationCommand.of(
                MSG_ID_1, SENDER_ID, "발신자A", null, ROOM_ID,
                List.of(RECEIVER_1), "안녕하세요");

        handler.handleChatMessage(cmd);

        assertThatCode(() -> handler.handleChatMessage(cmd))
                .doesNotThrowAnyException();

        assertThat(jpaRepository.count()).isEqualTo(1L);
    }

    // ── 3. actor_id는 UNIQUE 키 외: 다른 actor(→ 다른 target_id)는 별건 저장 ────

    @Test
    @DisplayName("서로 다른 actor의 좋아요는 likeId(target_id)가 달라 각각 별건 알림으로 저장된다")
    void handlePostEvent_differentActors_differentLikeIds_savedAsTwoSeparateNotifications() {
        PostNotificationCommand cmdA = PostNotificationCommand.of(
                "POST_LIKED", LIKE_ID_1, POST_ID, ACTOR_A, "닉네임A", OWNER_ID);
        PostNotificationCommand cmdB = PostNotificationCommand.of(
                "POST_LIKED", LIKE_ID_2, POST_ID, ACTOR_B, "닉네임B", OWNER_ID);

        handler.handlePostEvent(cmdA);
        assertThatCode(() -> handler.handlePostEvent(cmdB)).doesNotThrowAnyException();

        assertThat(jpaRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("동일 messageId라도 수신자(receiver_id)가 다르면 별건 알림으로 저장된다")
    void handleChatMessage_sameMessageId_differentReceiverIds_savedAsTwoSeparateNotifications() {
        ChatNotificationCommand cmd = ChatNotificationCommand.of(
                MSG_ID_1, SENDER_ID, "발신자A", null, ROOM_ID,
                List.of(RECEIVER_1, RECEIVER_2), "안녕하세요");

        handler.handleChatMessage(cmd);

        assertThat(jpaRepository.count()).isEqualTo(2L);
    }
}