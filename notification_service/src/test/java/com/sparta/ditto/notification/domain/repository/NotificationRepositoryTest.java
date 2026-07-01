package com.sparta.ditto.notification.domain.repository;

import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import com.sparta.ditto.notification.infrastructure.persistence.NotificationRepositoryImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationRepositoryImpl.class)
class NotificationRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {
        @Bean
        org.springframework.data.domain.AuditorAware<UUID> auditorAwareImpl() {
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
    private NotificationRepository notificationRepository;

    private Notification createAndSave(UUID receiverId, NotificationType type,
                                       boolean isRead, String metaData) {
        TargetType targetType = switch (type) {
            case LIKE -> TargetType.POST;
            case COMMENT -> TargetType.COMMENT;
            case CHAT_MESSAGE -> TargetType.CHAT_MESSAGE;
        };
        Notification n = Notification.create(
                receiverId, UUID.randomUUID(), type, targetType,
                UUID.randomUUID().toString(), "테스트 메시지", metaData);
        if (isRead) {
            n.read();
        }
        return notificationRepository.save(n);
    }

    @Test
    @DisplayName("내 알림만 최신순으로 반환하고, 타인 알림은 포함하지 않는다")
    void findNotificationsWithCursor_내알림최신순_타인알림제외() throws InterruptedException {
        // given
        UUID myId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();

        Notification older = createAndSave(myId, NotificationType.LIKE, false, null);
        Thread.sleep(10);
        Notification newer = createAndSave(myId, NotificationType.COMMENT, false, null);
        createAndSave(otherId, NotificationType.LIKE, false, null);

        // when
        List<Notification> result = notificationRepository.findNotificationsWithCursor(
                myId, null, null, 20);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(newer.getId());
        assertThat(result.get(1).getId()).isEqualTo(older.getId());
        assertThat(result).allMatch(n -> n.getReceiverId().equals(myId));
    }

    @Test
    @DisplayName("알림이 없으면 빈 목록을 반환한다")
    void findNotificationsWithCursor_알림없음_빈목록() {
        // given - 저장된 알림 없음

        // when
        List<Notification> result = notificationRepository.findNotificationsWithCursor(
                UUID.randomUUID(), null, null, 20);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("cursor 알림보다 오래된 데이터만 반환된다")
    void findNotificationsWithCursor_cursor적용_오래된데이터만() throws InterruptedException {
        // given
        UUID receiverId = UUID.randomUUID();

        Notification oldest = createAndSave(receiverId, NotificationType.LIKE, false, null);
        Thread.sleep(10);
        Notification middle = createAndSave(receiverId, NotificationType.LIKE, false, null);
        Thread.sleep(10);
        createAndSave(receiverId, NotificationType.LIKE, false, null);

        // when
        List<Notification> result = notificationRepository.findNotificationsWithCursor(
                receiverId, middle.getCreatedAt(), middle.getId(), 20);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(oldest.getId());
    }

    @Test
    @DisplayName("데이터가 size+1개 이상이면 초과분이 반환되어 hasNext 판단이 가능하다")
    void findNotificationsWithCursor_size초과_hasNext판단가능() {
        // given
        UUID receiverId = UUID.randomUUID();
        int size = 2;
        for (int i = 0; i < 3; i++) {
            createAndSave(receiverId, NotificationType.LIKE, false, null);
        }

        // when
        List<Notification> result = notificationRepository.findNotificationsWithCursor(
                receiverId, null, null, size + 1);

        // then
        assertThat(result.size()).isGreaterThan(size);
    }

    @Test
    @DisplayName("본인의 is_read=false 알림 수만 집계한다")
    void countUnreadByReceiverId_미읽음수만집계() {
        // given
        UUID receiverId = UUID.randomUUID();
        createAndSave(receiverId, NotificationType.LIKE, false, null);
        createAndSave(receiverId, NotificationType.COMMENT, false, null);
        createAndSave(receiverId, NotificationType.LIKE, true, null);
        createAndSave(UUID.randomUUID(), NotificationType.LIKE, false, null);

        // when
        long count = notificationRepository.countUnreadByReceiverId(receiverId);

        // then
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("본인의 미읽음 채팅 알림만 반환한다 (읽음·타인·비채팅 제외)")
    void findUnreadChatByReceiverId_미읽음채팅알림반환() {
        // given
        UUID receiverId = UUID.randomUUID();
        String chatMeta = "{\"roomId\":\"room1\",\"senderNickname\":\"A\",\"senderProfileImageUrl\":null}";

        Notification unread1 = createAndSave(receiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);
        Notification unread2 = createAndSave(receiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);
        createAndSave(receiverId, NotificationType.CHAT_MESSAGE, true, chatMeta);       // 읽음 제외
        createAndSave(receiverId, NotificationType.LIKE, false, null);                  // 비채팅 제외
        createAndSave(UUID.randomUUID(), NotificationType.CHAT_MESSAGE, false, chatMeta); // 타인 제외

        // when
        List<Notification> result = notificationRepository.findUnreadChatByReceiverId(receiverId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Notification::getId)
                .containsExactlyInAnyOrder(unread1.getId(), unread2.getId());
    }
}
