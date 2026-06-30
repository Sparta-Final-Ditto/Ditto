package com.sparta.ditto.feed.infrastructure.client.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.infrastructure.client.user.dto.FollowingResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * FollowServiceAdapter 단위 테스트.
 * UserServiceClient를 Mock으로 격리하고 FollowingResponse → FollowingResult 변환 로직을 검증한다.
 * user-service 실제 응답에는 nickname/profileImageUrl/bio 필드가 존재하지만,
 * FollowingUser record는 UUID id만 선언하므로 나머지 필드는 Jackson이 무시하며
 * 변환 결과에도 포함되지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceAdapterTest {

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private FollowServiceAdapter followServiceAdapter;

    @Test
    @DisplayName("user-service 정상 응답 시 data[].id만 추출하여 FollowingResult로 변환된다")
    void getFollowingIds_convertsResponseToResult() {
        UUID userId = UUID.randomUUID();
        UUID followingA = UUID.randomUUID();
        UUID followingB = UUID.randomUUID();
        given(userServiceClient.getFollowings(userId))
                .willReturn(new FollowingResponse(200, "SUCCESS",
                        List.of(new FollowingResponse.FollowingUser(followingA),
                                new FollowingResponse.FollowingUser(followingB))));

        FollowingResult result = followServiceAdapter.getFollowingIds(userId);

        // data[].id만 추출 — nickname/profileImageUrl/bio는 FollowingUser에 없으므로 구조적으로 무시
        assertThat(result.followingUserIds()).containsExactly(followingA, followingB);
    }

    @Test
    @DisplayName("data가 null이면 빈 목록을 반환한다")
    void getFollowingIds_returnsEmptyWhenDataNull() {
        UUID userId = UUID.randomUUID();
        given(userServiceClient.getFollowings(userId))
                .willReturn(new FollowingResponse(200, "SUCCESS", null));

        FollowingResult result = followServiceAdapter.getFollowingIds(userId);

        assertThat(result.followingUserIds()).isEmpty();
    }
}
