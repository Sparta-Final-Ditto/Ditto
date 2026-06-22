package com.sparta.ditto.chat.application.message;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.ChatRoomMetadataService;
import com.sparta.ditto.chat.domain.message.MessageIdGenerator;
import com.sparta.ditto.chat.domain.message.MessageType;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageDocument;
import com.sparta.ditto.chat.infrastructure.mongo.ChatMessageMongoRepository;
import com.sparta.ditto.chat.presentation.dto.response.ChatMessageResponse;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatMessageAckResponse;
import com.sparta.ditto.chat.presentation.dto.stomp.ChatMessageSendRequest;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatMessageSendService {

    private static final String ROOM_DESTINATION_PREFIX = "/sub/chat/rooms/";
    private static final String ACK_DESTINATION = "/sub/chat/messages/ack";

    private final ChatMessageMongoRepository chatMessageMongoRepository;
    private final ChatParticipantValidator chatParticipantValidator;
    private final MessageIdGenerator messageIdGenerator;
    private final ChatRoomMetadataService chatRoomMetadataService;
    private final SimpMessagingTemplate messagingTemplate;

    // 사용자 메시지 전송: 검증 → 저장 → last message 갱신 → ACK → 브로드캐스트
    public void sendUserMessage(UUID roomId, UUID senderId, ChatMessageSendRequest request) {
        chatParticipantValidator.ensureActiveParticipant(roomId, senderId);
        chatParticipantValidator.ensureRoomActive(roomId);
        validateUserContent(request);

        String messageId = messageIdGenerator.generate();
        ChatMessageDocument saved = chatMessageMongoRepository.save(
                ChatMessageDocument.createUserMessage(
                        messageId,
                        roomId,
                        senderId,
                        request.clientMessageId(),
                        request.messageType(),
                        request.content()
                )
        );

        chatRoomMetadataService.updateLastMessage(
                roomId, saved.getMessageId(), saved.getCreatedAt());

        // ACK(발신자) 먼저 그 다음 브로드캐스트
        ChatMessageAckResponse ack = ChatMessageAckResponse.of(
                roomId, request.clientMessageId(), saved.getMessageId(), saved.getCreatedAt());
        messagingTemplate.convertAndSendToUser(senderId.toString(), ACK_DESTINATION, ack);
        messagingTemplate.convertAndSend(
                ROOM_DESTINATION_PREFIX + roomId, ChatMessageResponse.from(saved));
    }

    // 시스템 메시지 저장 + 브로드캐스트
    public ChatMessageResponse saveSystemMessage(
            UUID roomId, UUID actorId, MessageType messageType, String content) {
        String messageId = messageIdGenerator.generate();
        ChatMessageDocument saved = chatMessageMongoRepository.save(
                ChatMessageDocument.createSystemMessage(
                        messageId, roomId, actorId, messageType, content));

        chatRoomMetadataService.updateLastMessage(
                roomId, saved.getMessageId(), saved.getCreatedAt());

        ChatMessageResponse response = ChatMessageResponse.from(saved);
        messagingTemplate.convertAndSend(ROOM_DESTINATION_PREFIX + roomId, response);
        return response;
    }

    private void validateUserContent(ChatMessageSendRequest request) {
        // TEXT: 본문 문자열 / IMAGE: URL·metadata 문자열
        if (request.messageType() == null
                || request.clientMessageId() == null
                || request.content() == null
                || request.content().isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (request.messageType().isSystem()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}