package com.sparta.ditto.assistant.domain.exception;

import com.sparta.ditto.common.exception.BusinessException;

/** ChatClient 호출(Ollama/API) 실패 시 발생 */
public class LlmResponseFailedException extends BusinessException {

    public LlmResponseFailedException() {
        super(AssistantErrorCode.LLM_RESPONSE_FAILED);
    }
}
