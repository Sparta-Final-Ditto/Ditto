package com.sparta.ditto.feed.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * user-service 차단 조회 포트.
 *
 * <p>좋아요·댓글 상호작용용 양방향 차단 확인을 제공한다. 양방향(요청자가 상대를 차단 /
 * 상대가 요청자를 차단) 판정은 user-service가 수행하며, feed는 그 결과(true=차단)만 받는다.
 * 이 포트는 "차단 여부"라는 사실만 반환하고, 차단 시 상호작용을 거부할지(예외 발생)는
 * Application/Domain 정책 결정으로 남긴다.</p>
 *
 * <p>차단 조회 자체가 실패(4xx/5xx/타임아웃)하면 예외를 전파한다. 실패 시 fail-open 여부는
 * Application이 결정하며, 이 포트(및 구현 Adapter)는 예외를 삼키지 않는다.</p>
 */
public interface UserBlockPort {

    /**
     * @return 요청자와 상대 사용자가 (어느 방향으로든) 차단 관계이면 {@code true}, 아니면 {@code false}
     */
    boolean isBlockedEitherDirection(UUID requesterId, UUID targetUserId);

    /**
     * 피드 단방향 필터용: 요청자가 차단한 사용자 ID 목록을 조회한다(me/blocks).
     * 조회 실패 시 예외를 전파하며, fail-open은 Application이 결정한다.
     */
    List<UUID> findBlockedUserIds(UUID requesterId);
}
