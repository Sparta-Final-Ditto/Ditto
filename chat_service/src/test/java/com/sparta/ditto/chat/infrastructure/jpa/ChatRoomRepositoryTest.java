package com.sparta.ditto.chat.infrastructure.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_room_repository_test;"
                + "MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("test")
@DisplayName("ChatRoomRepository 테스트")
class ChatRoomRepositoryTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID RECENT_ROOM_ID = UUID.randomUUID();
    private static final UUID OLD_ROOM_ID = UUID.randomUUID();
    private static final UUID EMPTY_ROOM_ID = UUID.randomUUID();

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("lastMessageAt 내림차순으로 정렬하고 마지막 메시지가 없는 방은 뒤로 보낸다")
    void findAllByIdsOrderByLastMessageAtDesc_success() {
        // given
        insertRoom(
                EMPTY_ROOM_ID,
                null,
                "2026-06-20T03:00:00Z"
        );
        insertRoom(
                OLD_ROOM_ID,
                "2026-06-19T01:00:00Z",
                "2026-06-20T02:00:00Z"
        );
        insertRoom(
                RECENT_ROOM_ID,
                "2026-06-20T01:00:00Z",
                "2026-06-20T01:00:00Z"
        );

        // when
        List<ChatRoom> chatRooms = chatRoomRepository.findAllByIdsOrderByLastMessageAtDesc(
                List.of(EMPTY_ROOM_ID, OLD_ROOM_ID, RECENT_ROOM_ID)
        );

        // then
        assertThat(chatRooms)
                .extracting(ChatRoom::getId)
                .containsExactly(RECENT_ROOM_ID, OLD_ROOM_ID, EMPTY_ROOM_ID);
    }

    private void insertRoom(
            UUID roomId,
            String lastMessageAt,
            String updatedAt
    ) {
        jdbcTemplate.update(
                """
                        INSERT INTO chat_rooms (
                            id,
                            room_type,
                            status,
                            created_by,
                            created_at,
                            updated_at,
                            last_message_at
                        )
                        VALUES (?, 'DIRECT', 'ACTIVE', ?, ?, ?, ?)
                        """,
                roomId,
                USER_ID,
                OffsetDateTime.parse("2026-06-20T00:00:00Z"),
                OffsetDateTime.parse(updatedAt),
                lastMessageAt == null ? null : OffsetDateTime.parse(lastMessageAt)
        );
    }
}
