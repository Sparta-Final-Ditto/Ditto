package com.sparta.ditto.chat.application;

import com.sparta.ditto.chat.application.dto.ChatDirectRoomCreateCommand;
import com.sparta.ditto.chat.application.dto.ChatDirectRoomResult;
import com.sparta.ditto.chat.domain.exception.ChatInvalidDirectTargetException;
import com.sparta.ditto.chat.domain.exception.ChatRoomNotFoundException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import com.sparta.ditto.chat.domain.room.DirectChatPair;
import com.sparta.ditto.chat.domain.room.RoomStatus;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomParticipantRepository;
import com.sparta.ditto.chat.infrastructure.jpa.ChatRoomRepository;
import com.sparta.ditto.chat.infrastructure.jpa.DirectChatPairRepository;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ChatDirectRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomParticipantRepository chatRoomParticipantRepository;
    private final DirectChatPairRepository directChatPairRepository;
    private final TransactionTemplate transactionTemplate;

    public ChatDirectRoomResult createOrGetDirectRoom(ChatDirectRoomCreateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        UUID requesterId = command.requesterId();
        UUID targetUserId = command.targetUserId();
        validateDirectTarget(requesterId, targetUserId);

        // 1:1 채팅방은 두 사용자 ID를 항상 같은 순서로 정렬해야 중복 생성을 막을 수 있다.
        DirectChatPair.OrderedUserIds orderedUserIds =
                DirectChatPair.orderUserIds(requesterId, targetUserId);

        ChatDirectRoomResult existingRoom = resolveExistingRoom(orderedUserIds);
        if (existingRoom != null) {
            return existingRoom;
        }

        try {
            return transactionTemplate.execute(
                    status -> createDirectRoom(requesterId, targetUserId)
            );
        } catch (DataIntegrityViolationException ex) {
            // 실패한 생성 트랜잭션과 분리된 새 트랜잭션에서 기존 방을 재조회한다.
            ChatDirectRoomResult roomAfterConflict = transactionTemplate.execute(
                    status -> findRoomAfterUniqueConflict(requesterId, targetUserId)
            );
            if (roomAfterConflict == null) {
                throw ex;
            }
            return roomAfterConflict;
        }
    }

    private ChatDirectRoomResult resolveExistingRoom(DirectChatPair.OrderedUserIds orderedUserIds) {
        return transactionTemplate.execute(status -> directChatPairRepository
                .findByUser1IdAndUser2Id(orderedUserIds.user1Id(), orderedUserIds.user2Id())
                .map(this::returnExistingOrReactivateRoom)
                .orElse(null));
    }

    private void validateDirectTarget(UUID requesterId, UUID targetUserId) {
        if (requesterId == null) {
            throw new ChatInvalidDirectTargetException();
        }
        if (targetUserId == null) {
            throw new ChatInvalidDirectTargetException();
        }
        if (requesterId.equals(targetUserId)) {
            throw new ChatInvalidDirectTargetException();
        }
        // TODO: user-service 연동 방식 확정 후 차단 관계 검증을 추가한다.
    }

    private ChatDirectRoomResult returnExistingOrReactivateRoom(DirectChatPair directChatPair) {
        ChatRoom chatRoom = chatRoomRepository.findById(directChatPair.getRoomId())
                .orElseThrow(ChatRoomNotFoundException::new);

        boolean reactivated = false;
        if (chatRoom.getStatus() == RoomStatus.INACTIVE) {
            // 1:1 방은 direct_chat_pairs unique 구조 때문에 새 방을 만들지 않고 기존 방을 되살린다.
            chatRoom.reactivate();
            rejoinParticipants(chatRoom.getId());
            reactivated = true;
        }
        return ChatDirectRoomResult.of(chatRoom.getId(), chatRoom.getStatus(), false, reactivated);
    }

    private void rejoinParticipants(UUID roomId) {
        // 재활성화 시 나갔던 참여자 상태를 복구하되, lastVisibleMessageId는 보존한다.
        List<ChatRoomParticipant> participants =
                chatRoomParticipantRepository.findAllByRoomId(roomId);
        participants.forEach(ChatRoomParticipant::rejoin);
    }

    private ChatDirectRoomResult createDirectRoom(UUID requesterId, UUID targetUserId) {
        ChatRoom chatRoom = chatRoomRepository.saveAndFlush(ChatRoom.createDirect(requesterId));
        // 1:1 채팅방은 별도 방장 없이 두 사용자를 모두 MEMBER로 생성한다.
        chatRoomParticipantRepository.saveAll(List.of(
                ChatRoomParticipant.join(chatRoom.getId(), requesterId, ParticipantRole.MEMBER),
                ChatRoomParticipant.join(chatRoom.getId(), targetUserId, ParticipantRole.MEMBER)
        ));
        directChatPairRepository.saveAndFlush(DirectChatPair.create(
                chatRoom.getId(),
                requesterId,
                targetUserId
        ));
        return ChatDirectRoomResult.of(chatRoom.getId(), chatRoom.getStatus(), true, false);
    }

    private ChatDirectRoomResult findRoomAfterUniqueConflict(
            UUID requesterId,
            UUID targetUserId
    ) {
        DirectChatPair.OrderedUserIds orderedUserIds =
                DirectChatPair.orderUserIds(requesterId, targetUserId);
        return directChatPairRepository
                .findByUser1IdAndUser2Id(orderedUserIds.user1Id(), orderedUserIds.user2Id())
                .map(this::returnExistingOrReactivateRoom)
                .orElse(null);
    }
}
