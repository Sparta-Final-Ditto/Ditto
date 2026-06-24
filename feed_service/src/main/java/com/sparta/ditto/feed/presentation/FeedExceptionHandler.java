package com.sparta.ditto.feed.presentation;

import com.sparta.ditto.common.response.ApiResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * feed-service 전용 예외 핸들러.
 * GlobalExceptionHandler(common)보다 먼저 처리되도록 HIGHEST_PRECEDENCE 적용.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeedExceptionHandler {

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(400, "VALIDATION_ERROR", "유효하지 않은 요청 데이터입니다."));
    }
}
