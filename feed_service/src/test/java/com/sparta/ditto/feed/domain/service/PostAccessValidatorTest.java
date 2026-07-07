package com.sparta.ditto.feed.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sparta.ditto.feed.domain.exception.PostNotFoundException;
import com.sparta.ditto.feed.domain.type.Visibility;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 단건 게시글 접근제어 규칙({@link PostAccessValidator}) 단위 테스트.
 * 팔로우 확인({@link BooleanSupplier})은 FOLLOWERS_ONLY이고 작성자 본인이 아닐 때만
 * 지연 평가되는지(불필요한 외부 호출 방지)도 함께 검증한다.
 */
class PostAccessValidatorTest {

    private final UUID owner = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    /** 호출되면 테스트 실패 — 팔로우 확인이 일어나면 안 되는 케이스에 사용한다. */
    private BooleanSupplier followerCheckMustNotRun() {
        return () -> {
            throw new AssertionError("팔로우 확인(외부 호출)이 일어나면 안 된다");
        };
    }

    @Test
    @DisplayName("작성자 본인은 visibility와 무관하게 접근 가능하며 팔로우 확인을 하지 않는다")
    void author_alwaysAllowed_withoutFollowerCheck() {
        for (Visibility v : Visibility.values()) {
            assertThatCode(() ->
                    PostAccessValidator.validate(v, owner, owner, followerCheckMustNotRun()))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("PUBLIC은 누구나 접근 가능하며 팔로우 확인을 하지 않는다")
    void publicPost_allowedForAnyone_withoutFollowerCheck() {
        assertThatCode(() ->
                PostAccessValidator.validate(Visibility.PUBLIC, owner, other, followerCheckMustNotRun()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PRIVATE은 작성자가 아니면 404(POST_NOT_FOUND)이며 팔로우 확인을 하지 않는다")
    void privatePost_deniedForNonAuthor_asNotFound() {
        assertThatThrownBy(() ->
                PostAccessValidator.validate(Visibility.PRIVATE, owner, other, followerCheckMustNotRun()))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("FOLLOWERS_ONLY은 팔로워면 접근 가능하다")
    void followersOnly_allowedForFollower() {
        assertThatCode(() ->
                PostAccessValidator.validate(Visibility.FOLLOWERS_ONLY, owner, other, () -> true))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FOLLOWERS_ONLY은 팔로워가 아니면 404(POST_NOT_FOUND)")
    void followersOnly_deniedForNonFollower_asNotFound() {
        assertThatThrownBy(() ->
                PostAccessValidator.validate(Visibility.FOLLOWERS_ONLY, owner, other, () -> false))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("FOLLOWERS_ONLY 비작성자일 때만 팔로우 확인이 1회 평가된다")
    void followersOnly_evaluatesFollowerCheckOnce() {
        AtomicInteger calls = new AtomicInteger();
        PostAccessValidator.validate(Visibility.FOLLOWERS_ONLY, owner, other, () -> {
            calls.incrementAndGet();
            return true;
        });
        assertThat(calls.get()).isEqualTo(1);
    }
}
