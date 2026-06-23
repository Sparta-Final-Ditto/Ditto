package com.sparta.ditto.chat.application.room;

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
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatRoomQueryService {

    // TODO: B 메시지 조회 기능과 통합되면 lastMessage/unreadCount 계산 결과로 교체한다.
    private static final String LAST_MESSAGE_NOT_CONNECTED = null;
    private static final long UNREAD_COUNT_NOT_CONNECTED = 0L;

    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;

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
        return chatRoomPort.findAllByIdsOrderByLastMessageAtDesc(roomIds)
                .stream()
                .map(chatRoom -> toSummaryResult(
                        chatRoom,
                        participantByRoomId.get(chatRoom.getId())
                ))
                .toList();
    }

    private static ChatParticipantResult toParticipantResult(ChatRoomParticipant participant) {
        return ChatParticipantResult.of(
                participant.getUserId(),
                participant.getRole(),
                participant.getJoinedAt(),
                participant.getLeftAt()
        );
    }

    private static ChatRoomSummaryResult toSummaryResult(
            ChatRoom chatRoom,
            ChatRoomParticipant participant
    ) {
        return ChatRoomSummaryResult.of(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getRoomName(),
                LAST_MESSAGE_NOT_CONNECTED,
                chatRoom.getLastMessageAt(),
                UNREAD_COUNT_NOT_CONNECTED,
                participant.isNotificationEnabled()
        );
    }
}
