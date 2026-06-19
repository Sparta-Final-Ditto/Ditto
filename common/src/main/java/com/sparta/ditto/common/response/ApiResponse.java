package com.sparta.ditto.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int status;
    private final String code;
    private final String message;
    private final T data;
    private final List<String> errors;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), null, "SUCCESS", data, null);
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(HttpStatus.OK.value(), null, "SUCCESS", null, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), null, "CREATED", data, null);
    }

    public static <T> ApiResponse<T> updated(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), null, "UPDATED", data, null);
    }

    public static <T> ApiResponse<Void> deleted() {
        return new ApiResponse<>(HttpStatus.OK.value(), null, "DELETED", null, null);
    }

    public static <T> ApiResponse<Void> accepted() {
        return new ApiResponse<>(HttpStatus.ACCEPTED.value(), null, "ACCEPTED", null, null);
    }

    public static <T> ApiResponse<T> error(int status, String code, String message) {
        return new ApiResponse<>(status, code, message, null, null);
    }

    public static <T> ApiResponse<T> error(int status, String code, String message, List<String> errors) {
        return new ApiResponse<>(status, code, message, null, errors);
    }

    public static <T> ApiResponse<T> error(HttpStatus status, String code, String message) {
        return new ApiResponse<>(status.value(), code, message, null, null);
    }
}