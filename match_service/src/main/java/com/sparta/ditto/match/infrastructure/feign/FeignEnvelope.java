package com.sparta.ditto.match.infrastructure.feign;

import java.util.List;

/**
 * embedding_service / user_service 등 외부 서비스가 내려주는
 * {status, code, message, data, errors} 공통 응답 포맷을 Feign 클라이언트에서
 * 역직렬화하기 위한 전용 래퍼.
 *
 * common.response.ApiResponse는 컨트롤러가 응답을 직렬화(생성)하는 용도로만 쓰이는
 * private 생성자 클래스라 Jackson이 역직렬화(파싱)할 수 없다. Feign 응답 파싱에는
 * record 기반의 이 타입을 대신 사용한다.
 */
public record FeignEnvelope<T>(
        int status,
        String code,
        String message,
        T data,
        List<String> errors
) {
}
