package com.sparta.ditto.user.domain.follow.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FollowErrorCode implements ErrorCode {

    ALREADY_FOLLOWING("FOLLOW-001", "이미 팔로우한 사용자입니다.", 409),
    NOT_FOLLOWING("FOLLOW-002", "팔로우하지 않은 사용자입니다.", 400),
    CANNOT_SELF_FOLLOW("FOLLOW-003", "자기 자신을 팔로우할 수 없습니다.", 400);

    private final String code;
    private final String message;
    private final int status;
}
