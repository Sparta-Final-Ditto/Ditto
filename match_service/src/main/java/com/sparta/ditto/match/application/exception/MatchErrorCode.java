package com.sparta.ditto.match.application.exception;

import com.sparta.ditto.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MatchErrorCode implements ErrorCode {
    ALREADY_MATCHED_TODAY("MATCH-001", "오늘 이미 매칭을 진행했습니다.", 400),
    MATCH_NOT_FOUND("MATCH-002", "매칭 이력이 없습니다.", 404),
    NOT_ENOUGH_RECORDS("MATCH-003", "기록이 3개 이상이어야 매칭이 가능합니다.", 400),
    NO_MATCHING_CANDIDATE("MATCH-004", "매칭 가능한 상대가 없습니다.", 404);

    private final String code;
    private final String message;
    private final int status;
}
