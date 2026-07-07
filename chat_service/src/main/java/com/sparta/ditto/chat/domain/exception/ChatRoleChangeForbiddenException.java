package com.sparta.ditto.chat.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

public class ChatRoleChangeForbiddenException extends BusinessException {
  public ChatRoleChangeForbiddenException() {
    super(ChatErrorCode.CHAT_ROLE_CHANGE_FORBIDDEN);
  }
}
