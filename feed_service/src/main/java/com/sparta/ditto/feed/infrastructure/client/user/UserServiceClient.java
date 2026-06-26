package com.sparta.ditto.feed.infrastructure.client.user;

import com.sparta.ditto.feed.infrastructure.client.user.dto.FollowingResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-service", url = "${app.user-service.base-url}")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{userId}/followings")
    FollowingResponse getFollowings(@PathVariable UUID userId);
}
