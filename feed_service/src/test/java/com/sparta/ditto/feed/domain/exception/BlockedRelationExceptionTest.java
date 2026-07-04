package com.sparta.ditto.feed.domain.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.sparta.ditto.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * BlockedRelationException 단위 테스트 (Spring 컨텍스트 없이 POJO).
 *
 * <p>차단 관계에서 좋아요·댓글 상호작용을 시도할 때 발생하는 예외가
 * BLOCKED_RELATION ErrorCode(HTTP 403)를 담고 있는지 검증한다.
 * 한글 메시지 텍스트 일치는 검증하지 않는다(TEST_CASES 2장 ⑤ 깨지기 쉬운 테스트 방지).</p>
 */
class BlockedRelationExceptionTest {

    @Test
    @DisplayName("생성 시 ErrorCode가 BLOCKED_RELATION이고 HTTP status가 403이다")
    void carriesBlockedRelationErrorCodeWith403() {
        BlockedRelationException exception = new BlockedRelationException();

        assertThat(exception).isInstanceOf(BusinessException.class);
        assertThat(exception.getErrorCode().getCode()).isEqualTo("BLOCKED_RELATION");
        assertThat(exception.getErrorCode().getStatus()).isEqualTo(403);
    }
}