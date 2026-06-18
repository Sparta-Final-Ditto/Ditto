package com.sparta.ditto.common.exception;

public interface ErrorCode {
    String getCode();
    String getMessage();
    int getStatus();
}