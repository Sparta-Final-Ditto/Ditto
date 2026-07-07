package com.sparta.ditto.notification.domain.repository;

import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import com.sparta.ditto.notification.infrastructure.persistence.NotificationJpaRepository;
import com.sparta.ditto.notification.infrastructure.persistence.NotificationRepositoryImpl;
import com.sparta.ditto.notification.support.AbstractPostgresContainerTest;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationRepositoryImpl.class)
class NotificationRepositoryTest extends AbstractPostgresContainerTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class AuditingConfig {
        @Bean
        org.springframework.data.domain.AuditorAware<UUID> auditorAwareImpl() {
            return Optional::empty;
        }
    }

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationJpaRepository jpaRepository;

    private Notification createAndSave(UUID receiverId, NotificationType type,
                                       boolean isRead, String metaData) {
        TargetType targetType = switch (type) {
            case LIKE -> TargetType.LIKE;
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

    // ── markAsReadByIds ───────────────────────────────────────────────────────

    @Test
    @DisplayName("주어진 id의 미읽음 알림이 is_read=true로 변경되고 반환값이 변경 건수와 일치한다")
    void markAsReadByIds_변경대상_is_read_true이고_반환값일치() {
        // given
        UUID receiverId = UUID.randomUUID();
        String chatMeta = "{\"roomId\":\"room1\"}";
        Notification n1 = createAndSave(receiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);
        Notification n2 = createAndSave(receiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);
        Notification n3 = createAndSave(receiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);

        // when - n1, n2만 대상, n3는 제외
        int updatedCount = notificationRepository.markAsReadByIds(List.of(n1.getId(), n2.getId()));

        // then
        assertThat(updatedCount).isEqualTo(2);
        assertThat(jpaRepository.findById(n1.getId())).map(Notification::isRead).contains(true);
        assertThat(jpaRepository.findById(n2.getId())).map(Notification::isRead).contains(true);
        assertThat(jpaRepository.findById(n3.getId())).map(Notification::isRead).contains(false);
    }

    @Test
    @DisplayName("이미 읽은 알림은 WHERE is_read=false 조건으로 반환값에 포함되지 않는다")
    void markAsReadByIds_이미읽은알림_반환값미포함() {
        // given
        UUID receiverId = UUID.randomUUID();
        String chatMeta = "{\"roomId\":\"room1\"}";
        Notification unread1 = createAndSave(receiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);
        Notification unread2 = createAndSave(receiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);
        Notification alreadyRead1 = createAndSave(receiverId, NotificationType.CHAT_MESSAGE, true, chatMeta);
        Notification alreadyRead2 = createAndSave(receiverId, NotificationType.CHAT_MESSAGE, true, chatMeta);

        // when - 미읽음 2 + 이미읽음 2, 4개 모두 대상
        int updatedCount = notificationRepository.markAsReadByIds(
                List.of(unread1.getId(), unread2.getId(), alreadyRead1.getId(), alreadyRead2.getId()));

        // then - false→true로 실제 변경된 건만 2
        assertThat(updatedCount).isEqualTo(2);
    }

    @Test
    @DisplayName("id 목록에 없는 알림은 변경되지 않는다")
    void markAsReadByIds_id목록에없는알림_불변() {
        // given
        UUID targetReceiverId = UUID.randomUUID();
        UUID otherReceiverId = UUID.randomUUID();
        String chatMeta = "{\"roomId\":\"room1\"}";
        Notification target1 = createAndSave(targetReceiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);
        Notification target2 = createAndSave(targetReceiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);
        Notification other1 = createAndSave(otherReceiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);
        Notification other2 = createAndSave(otherReceiverId, NotificationType.CHAT_MESSAGE, false, chatMeta);

        // when - targetReceiverId의 id만 전달
        int updatedCount = notificationRepository.markAsReadByIds(List.of(target1.getId(), target2.getId()));

        // then
        assertThat(updatedCount).isEqualTo(2);
        assertThat(jpaRepository.findById(other1.getId())).map(Notification::isRead).contains(false);
        assertThat(jpaRepository.findById(other2.getId())).map(Notification::isRead).contains(false);
    }
}
