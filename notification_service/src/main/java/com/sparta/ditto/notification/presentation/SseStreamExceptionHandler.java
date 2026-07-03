package com.sparta.ditto.notification.presentation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

/**
 * SSE 스트림(text/event-stream)의 비동기 타임아웃을 조용히 처리한다. emitter 타임아웃 시
 * 발생하는 AsyncRequestTimeoutException은 정상적인 연결 종료이므로, 공통
 * GlobalExceptionHandler가 스트림에 JSON 에러를 쓰지 않도록 여기서 본문 없이 흡수한다
 * (반환 없음 → 응답 본문 미기록, debug 로그만).
 */
@RestControllerAdvice
public class SseStreamExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(SseStreamExceptionHandler.class);

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.debug("SSE 연결 타임아웃으로 종료 (정상)", e);
    }
}