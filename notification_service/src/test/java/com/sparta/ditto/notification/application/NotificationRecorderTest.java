package com.sparta.ditto.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.ditto.notification.application.dto.ChatNotificationCommand;
import com.sparta.ditto.notification.application.dto.PostNotificationCommand;
import com.sparta.ditto.notification.application.port.MetaDataPort;
import com.sparta.ditto.notification.domain.entity.Notification;
import com.sparta.ditto.notification.domain.repository.NotificationRepository;
import com.sparta.ditto.notification.domain.type.NotificationType;
import com.sparta.ditto.notification.domain.type.TargetType;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRecorder - 알림 생성/저장 (message 규칙·meta_data·수신자별 저장)")
class NotificationRecorderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final String ACTOR_NICKNAME = "새벽러너";
    private static final String POST_ID = "post_abc123";
    private static final String LIKE_ID = "like_def789";
    private static final String COMMENT_ID = "comment_def456";

    private static final String MESSAGE_ID = "018f7b7a-4d3c-7c22-9f1b-2a3c4d5e6f70";
    private static final UUID SENDER_ID = UUID.randomUUID();
    private static final String SENDER_NICKNAME = "짱구";
    private static final String SENDER_PROFILE_URL = "https://cdn.example.com/profiles/jjanggu.png";
    private static final String ROOM_ID = "3f6e9a62-6c3f-4ad2-9b2d-47a0e3d4b123";
    private static final UUID RECEIVER_1 = UUID.randomUUID();
    private static final UUID RECEIVER_2 = UUID.randomUUID();

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MetaDataPort metaDataPort;

    @InjectMocks
    private NotificationRecorder recorder;

    @BeforeEach
    void setUpMetaDataStubs() {
        lenient().when(metaDataPort.buildPostMetaData(POST_ID))
                 .thenReturn("{\"postId\":\"" + POST_ID + "\"}");
        lenient().when(metaDataPort.buildChatMetaData(ROOM_ID, SENDER_NICKNAME, null))
                 .thenReturn("{\"roomId\":\"" + ROOM_ID
                         + "\",\"senderNickname\":\"" + SENDER_NICKNAME
                         + "\",\"senderProfileImageUrl\":null}");
        lenient().when(metaDataPort.buildChatMetaData(ROOM_ID, SENDER_NICKNAME, SENDER_PROFILE_URL))
                 .thenReturn("{\"roomId\":\"" + ROOM_ID
                         + "\",\"senderNickname\":\"" + SENDER_NICKNAME
                         + "\",\"senderProfileImageUrl\":\"" + SENDER_PROFILE_URL + "\"}");
    }

    // ── post-events LIKE/COMMENT 알림 생성 ────────────────────────────────────

    static Stream<Arguments> postLikeAndCommentScenarios() {
        return Stream.of(
                Arguments.of("POST_LIKED", LIKE_ID, NotificationType.LIKE, TargetType.LIKE, "좋아요"),
                Arguments.of("POST_COMMENTED", COMMENT_ID, NotificationType.COMMENT, TargetType.COMMENT, "댓글")
        );
    }

    @ParameterizedTest(name = "{0} → {2} 알림 생성")
    @MethodSource("postLikeAndCommentScenarios")
    @DisplayName("POST_LIKED/POST_COMMENTED는 작성자에게 알림을 생성한다 "
            + "(type/receiver=owner/actor/target_id=리소스ID/message는 actorNickname 치환/meta_data=postId)")
    void recordPost_createsLikeOrCommentNotification(
            String eventType,
            String targetId,
            NotificationType expectedType,
            TargetType expectedTargetType,
            String messageToken
    ) throws Exception {
        // Given
        PostNotificationCommand command = PostNotificationCommand.of(
                eventType, targetId, POST_ID, ACTOR_ID, ACTOR_NICKNAME, OWNER_ID);

        // When
        recorder.recordPost(command);

        // Then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        assertThat(saved.getType()).isEqualTo(expectedType);
        assertThat(saved.getTargetType()).isEqualTo(expectedTargetType);
        assertThat(saved.getReceiverId()).isEqualTo(OWNER_ID);   // receiver_id = ownerId
        assertThat(saved.getActorId()).isEqualTo(ACTOR_ID);      // actor_id = 행위자(userId)

        // target_id = likeId/commentId (postId 아님)
        assertThat(saved.getTargetId()).isEqualTo(targetId);
        assertThat(saved.getTargetId()).isNotEqualTo(POST_ID);

        // message: actorNickname 치환 + 타입별 문구 규칙 (전체 문자열 일치 검증 금지)
        assertThat(saved.getMessage()).startsWith(ACTOR_NICKNAME);
        assertThat(saved.getMessage()).contains(messageToken);

        // meta_data = {"postId":"..."}
        JsonNode meta = OBJECT_MAPPER.readTree(saved.getMetaData());
        assertThat(meta.get("postId").asText()).isEqualTo(POST_ID);
        assertThat(meta.has("roomId")).isFalse();
    }

    // ── 채팅 수신자별 개별 저장 + preview 그대로 message 저장 ──────────────────

    static Stream<String> chatPreviews() {
        return Stream.of(
                "오늘 같이 공부하실래요?",                 // TEXT
                "사진을 보냈습니다",                        // IMAGE 대체 문구
                "동영상을 보냈습니다",                      // VIDEO 대체 문구
                "가".repeat(200)                            // 긴 문구 (재가공 없이 그대로)
        );
    }

    @ParameterizedTest(name = "preview=\"{0}\"")
    @MethodSource("chatPreviews")
    @DisplayName("chat-message-created는 receiverIds N명에게 각각 저장하고, "
            + "preview를 재가공 없이 그대로 message로 저장한다 (senderProfileImageUrl=null 허용)")
    void recordChat_savesPerReceiverWithPreviewAsMessage(String preview) {
        // Given: 수신자 2명, 프로필 null
        ChatNotificationCommand command = ChatNotificationCommand.of(
                MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, null, ROOM_ID,
                List.of(RECEIVER_1, RECEIVER_2), preview);

        // When
        recorder.recordChat(command);

        // Then: 이벤트 1 → 알림 2건 개별 저장
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(2)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();

        assertThat(saved)
                .extracting(Notification::getReceiverId)
                .containsExactlyInAnyOrder(RECEIVER_1, RECEIVER_2);

        assertThat(saved).allSatisfy(n -> {
            assertThat(n.getType()).isEqualTo(NotificationType.CHAT_MESSAGE);
            assertThat(n.getTargetType()).isEqualTo(TargetType.CHAT_MESSAGE);
            assertThat(n.getActorId()).isEqualTo(SENDER_ID);          // actor_id = senderId
            assertThat(n.getTargetId()).isEqualTo(MESSAGE_ID);        // target_id = messageId
            assertThat(n.getMessage()).isEqualTo(preview);            // 재가공 없이 그대로

            JsonNode meta = readTree(n.getMetaData());
            assertThat(meta.get("roomId").asText()).isEqualTo(ROOM_ID);
            assertThat(meta.get("senderNickname").asText()).isEqualTo(SENDER_NICKNAME);
            assertThat(meta.get("senderProfileImageUrl").isNull()).isTrue();  // null 허용
        });
    }

    @Test
    @DisplayName("senderProfileImageUrl이 있으면 meta_data에 그대로 저장한다")
    void recordChat_storesNonNullProfileImageUrl() {
        // Given
        ChatNotificationCommand command = ChatNotificationCommand.of(
                MESSAGE_ID, SENDER_ID, SENDER_NICKNAME, SENDER_PROFILE_URL, ROOM_ID,
                List.of(RECEIVER_1), "안녕하세요");

        // When
        recorder.recordChat(command);

        // Then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        JsonNode meta = readTree(captor.getValue().getMetaData());
        assertThat(meta.get("senderProfileImageUrl").asText()).isEqualTo(SENDER_PROFILE_URL);
    }

    private static JsonNode readTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}