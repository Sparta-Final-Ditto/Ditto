package com.sparta.ditto.chat.application.room;

import com.sparta.ditto.chat.application.participant.ChatParticipantValidator;
import com.sparta.ditto.chat.application.room.dto.command.ChatNotificationSettingCommand;
import com.sparta.ditto.chat.application.room.dto.result.ChatNotificationSettingResult;
import com.sparta.ditto.chat.application.room.port.ChatRoomParticipantPort;
import com.sparta.ditto.chat.domain.exception.ChatNotParticipantException;
import com.sparta.ditto.chat.domain.participant.ChatRoomParticipant;
import com.sparta.ditto.common.exception.BusinessException;
import com.sparta.ditto.common.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatNotificationSettingService {

    private final ChatParticipantValidator chatParticipantValidator;
    private final ChatRoomParticipantPort chatRoomParticipantPort;

    @Transactional
    public ChatNotificationSettingResult updateNotificationSetting(
            ChatNotificationSettingCommand command
    ) {
        if (command == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        chatParticipantValidator.ensureRoomActive(command.roomId());

        // 알림 설정은 현재 참여 중인 사용자 본인의 참여자 메타데이터만 변경한다.
        ChatRoomParticipant participant = chatRoomParticipantPort
                .findActiveParticipant(
                        command.roomId(),
                        command.requesterId()
                )
                .orElseThrow(ChatNotParticipantException::new);

        participant.changeNotificationEnabled(command.enabled());

        return ChatNotificationSettingResult.of(
                command.roomId(),
                participant.isNotificationEnabled()
        );
    }
}
