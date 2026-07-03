package com.sparta.ditto.assistant.domain.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AssistantErrorCode implements ErrorCode {

    LLM_RESPONSE_FAILED("ASSISTANT-001", "챗봇 응답 생성에 실패했습니다.", 500);

    private final String code;
    private final String message;
    private final int status;
}
