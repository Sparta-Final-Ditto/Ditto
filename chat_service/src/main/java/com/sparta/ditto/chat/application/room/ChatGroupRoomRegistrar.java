package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.message.ChatMessageSendService;
import com.sparta.ditto.chat.application.room.dto.result.ChatGroupRoomResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.application.room.port.ChatRoomPort;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.chat.domain.participant.ParticipantRole;
import com.sparta.ditto.chat.domain.room.ChatRoom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatGroupRoomRegistrar {

    private static final String SYSTEM_JOIN_CONTENT_FORMAT = "%s님이 채팅방을 생성했습니다.";

    private final ChatRoomPort chatRoomPort;
    private final ChatRoomParticipantPort chatRoomParticipantPort;
    private final ChatMessageSendService chatMessageSendService;

    // 외부 검증을 마친 뒤 방·참여자·시스템 메시지 저장만 짧은 트랜잭션으로 처리한다.
    @Transactional
    public ChatGroupRoomResult create(
            String roomName, UUID requesterId, List<UUID> memberUserIds, String creatorNickname) {
        ChatRoom chatRoom = chatRoomPort.save(ChatRoom.createGroup(roomName));

        // 그룹방 생성자는 OWNER, 나머지 참여자는 MEMBER로 저장한다.
        List<ChatRoomParticipant> participants = new ArrayList<>();
        participants.add(ChatRoomParticipant.join(
                chatRoom.getId(), requesterId, ParticipantRole.OWNER));
        memberUserIds.forEach(userId -> participants.add(ChatRoomParticipant.join(
                chatRoom.getId(), userId, ParticipantRole.MEMBER)));
        chatRoomParticipantPort.saveAll(participants);

        // 그룹방 생성 안내 시스템 메시지 저장
        chatMessageSendService.saveSystemMessage(
                chatRoom.getId(),
                requesterId,
                MessageType.SYSTEM_JOIN,
                String.format(SYSTEM_JOIN_CONTENT_FORMAT, creatorNickname)
        );

        log.info("Group chat room created. requesterId={}, roomId={}, participantCount={}",
                requesterId, chatRoom.getId(), participants.size());

        return ChatGroupRoomResult.of(
                chatRoom.getId(),
                chatRoom.getRoomType(),
                chatRoom.getRoomName(),
                chatRoom.getStatus()
        );
    }
}
