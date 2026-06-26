package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.message.dto.SentMessage;
import com.sparta.ditto.chat.application.message.port.ChatMessageQueryPort;
import com.sparta.ditto.chat.application.room.dto.result.ChatParticipantResult;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomDetailResult;
import com.sparta.ditto.chat.application.room.dto.result.ChatRoomSummaryResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomQueryService {

    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;
    private final ChatMessageQueryPort chatMessageQueryPort;

    @Transactional(readOnly = true)
    public ChatRoomDetailResult getRoom(UUID requesterId, UUID roomId) {
        ChatRoom chatRoom = chatRoomPort.findById(roomId)
                .orElseThrow(ChatRoomNotFoundException::new);
        // 상세 조회는 현재 참여 중인 사용자만 허용한다.
        ChatRoomParticipant requesterParticipant = chatRoomParticipantPort
                .findActiveParticipant(roomId, requesterId)
                .orElseThrow(ChatNotParticipantException::new);
        List<ChatParticipantResult> participants = chatRoomParticipantPort
                .findActiveParticipants(roomId)
                .stream()
                .map(ChatRoomQueryService::toParticipantResult)
                .toList();

        return ChatRoomDetailResult.of(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getRoomName(),
                chatRoom.getStatus(),
                participants,
                requesterParticipant.isNotificationEnabled()
        );
    }

    @Transactional(readOnly = true)
    public List<ChatRoomSummaryResult> getMyRooms(UUID requesterId) {
        // 기본 목록에서는 나가지 않았고 숨김 처리되지 않은 방만 노출한다.
        List<ChatRoomParticipant> myParticipants = chatRoomParticipantPort
                .findVisibleActiveParticipantsByUserId(requesterId);
        if (myParticipants.isEmpty()) {
            return List.of();
        }

        Map<UUID, ChatRoomParticipant> participantByRoomId = myParticipants.stream()
                .collect(Collectors.toMap(
                        ChatRoomParticipant::getRoomId,
                        Function.identity()
                ));
        List<UUID> roomIds = myParticipants.stream()
                .map(ChatRoomParticipant::getRoomId)
                .toList();

        // 방 목록 정렬은 PostgreSQL 메타데이터의 lastMessageAt을 우선 사용한다.
        List<ChatRoom> rooms = chatRoomPort.findAllByIdsOrderByLastMessageAtDesc(roomIds);

        // 방들의 lastMessageId로 마지막 메시지 본문을 한 번에 조회한다 (N+1 방지).
        Map<String, SentMessage> lastMessageById = findLastMessages(rooms);

        // 방마다 unreadCount를 한 번에 계산한다 (N+1 방지).
        Map<UUID, String> lastReadByRoom = myParticipants.stream()
                .collect(Collectors.toMap(
                        ChatRoomParticipant::getRoomId,
                        p -> p.getLastReadMessageId() != null ? p.getLastReadMessageId() : ""
                ));
        Map<UUID, Long> unreadCounts =
                chatMessageQueryPort.countUnreadBatch(lastReadByRoom, requesterId);

        return rooms.stream()
                .map(room -> toSummaryResult(
                        room,
                        participantByRoomId.get(room.getId()),
                        lastMessageById,
                        unreadCounts.getOrDefault(room.getId(), 0L)
                ))
                .toList();
    }

    private Map<String, SentMessage> findLastMessages(List<ChatRoom> rooms) {
        List<String> lastMessageIds = rooms.stream()
                .map(ChatRoom::getLastMessageId)
                .filter(Objects::nonNull)
                .toList();
        if (lastMessageIds.isEmpty()) {
            return Map.of();
        }
        return chatMessageQueryPort.findByMessageIds(lastMessageIds).stream()
                .collect(Collectors.toMap(SentMessage::messageId, Function.identity()));
    }

    private ChatRoomSummaryResult toSummaryResult(
            ChatRoom chatRoom,
            ChatRoomParticipant participant,
            Map<String, SentMessage> lastMessageById,
            long unreadCount
    ) {
        return ChatRoomSummaryResult.of(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getRoomName(),
                resolveLastMessagePreview(chatRoom, lastMessageById),
                chatRoom.getLastMessageAt(),
                unreadCount,
                participant.isNotificationEnabled()
        );
    }

    private String resolveLastMessagePreview(
            ChatRoom chatRoom, Map<String, SentMessage> lastMessageById) {
        String lastMessageId = chatRoom.getLastMessageId();
        if (lastMessageId == null) {
            return null;
        }
        SentMessage lastMessage = lastMessageById.get(lastMessageId);
        if (lastMessage == null) {
            return null;
        }
        // 삭제된 메시지는 본문 대신 null로 내려 표시 정책은 클라이언트가 정한다.
        if (lastMessage.deletedAt() != null) {
            return null;
        }
        return lastMessage.content();
    }

    private static ChatParticipantResult toParticipantResult(ChatRoomParticipant participant) {
        return ChatParticipantResult.of(
                participant.getUserId(),
                participant.getRole(),
                participant.getJoinedAt(),
                participant.getLeftAt()
        );
    }
}
