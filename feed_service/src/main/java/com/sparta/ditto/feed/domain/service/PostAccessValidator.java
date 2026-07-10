package com.sparta.ditto.feed.domain.service;

import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * 단건 게시글 접근제어(visibility) 도메인 규칙.
 *
 * <p>목록/피드 조회는 allowedScopes로 미리 필터링하지만, 단건 접근(상세·댓글·좋아요·상호작용)은
 * postId만 알면 도달할 수 있어 별도 검증이 필요하다. 검증 규칙을 이 한 곳에 모아 각 유스케이스가
 * 중복 if문 없이 재사용한다.</p>
 *
 * <ul>
 *   <li>작성자 본인({@code requesterId == ownerId}): 항상 접근 가능</li>
 *   <li>PUBLIC: 누구나 접근 가능</li>
 *   <li>FOLLOWERS_ONLY: requester가 작성자를 팔로우 중일 때만 접근 가능</li>
 *   <li>PRIVATE: 작성자만 접근 가능</li>
 * </ul>
 *
 * <p>접근 거부 시 존재 여부 자체를 숨기기 위해 403이 아니라 {@link PostNotFoundException}(404)을 던진다.
 * 팔로우 여부 판정은 외부 호출(Feign)을 유발할 수 있으므로 {@link BooleanSupplier}로 받아
 * FOLLOWERS_ONLY이고 작성자 본인이 아닐 때만 지연 평가한다(불필요한 외부 호출 방지).</p>
 */
public final class PostAccessValidator {

    private PostAccessValidator() {
    }

    public static void validate(
            Visibility visibility, UUID ownerId, UUID requesterId, BooleanSupplier followerCheck) {
        if (requesterId.equals(ownerId)) {
            return;
        }
        switch (visibility) {
            case PUBLIC -> {
                // 누구나 접근 가능
            }
            case FOLLOWERS_ONLY -> {
                if (!followerCheck.getAsBoolean()) {
                    throw new PostNotFoundException();
                }
            }
            case PRIVATE -> throw new PostNotFoundException();
            default -> throw new PostNotFoundException();
        }
    }
}
