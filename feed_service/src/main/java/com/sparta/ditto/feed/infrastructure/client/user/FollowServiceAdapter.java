package com.sparta.ditto.feed.infrastructure.client.user;

import com.sparta.ditto.feed.application.port.out.FollowServicePort;
import com.sparta.ditto.feed.application.port.out.dto.FollowingResult;
import com.sparta.ditto.feed.infrastructure.client.user.dto.FollowingResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FollowServiceAdapter implements FollowServicePort {

    private final UserServiceClient userServiceClient;

    @Override
    public FollowingResult getFollowingIds(UUID userId) {
        FollowingResponse response = userServiceClient.getFollowings(userId);
        List<UUID> followingIds = response.data() == null
                ? List.of()
                : response.data().stream()
                        .map(FollowingResponse.FollowingUser::id)
                        .toList();
        return new FollowingResult(followingIds);
    }
}
