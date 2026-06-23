package com.sparta.ditto.chat.infrastructure.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_room_lastmsg_test;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("ChatRoom last_message 영속성 테스트")
class ChatRoomLastMessagePersistenceTest {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("updateLastMessage 후 flush하면 last_message_id/at 컬럼이 실제로 갱신된다")
    void updateLastMessage_persistsColumns() {
        // given: last_message_* 가 null 인 ACTIVE 방을 native insert 로 준비
        UUID roomId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO chat_rooms (
                            id, room_type, status,
                            created_by, created_at, updated_at
                        )
                        VALUES (?, 'DIRECT', 'ACTIVE', ?, ?, ?)
                        """,
                roomId,
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                OffsetDateTime.parse("2026-06-20T00:00:00Z")
        );

        String messageId = "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
        Instant createdAt = Instant.parse("2026-06-20T01:00:00Z");

        // when: 영속 엔티티를 로드해 도메인 메서드 호출 후 flush (실제 dirty checking 경로)
        ChatRoom room = chatRoomRepository.findById(roomId).orElseThrow();
        room.updateLastMessage(messageId, createdAt);
        chatRoomRepository.flush();

        // then: DB 컬럼을 직접 조회해 검증
        String savedId = jdbcTemplate.queryForObject(
                "SELECT last_message_id FROM chat_rooms WHERE id = ?",
                String.class, roomId);
        assertThat(savedId).isEqualTo(messageId);
    }
}