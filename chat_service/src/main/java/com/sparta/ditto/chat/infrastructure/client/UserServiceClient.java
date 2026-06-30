package com.sparta.ditto.chat.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "user-service",
        url = "${chat.user-service.base-url}",
        configuration = UserServiceFeignConfig.class
)
public interface UserServiceClient {

    @PostMapping("/api/v1/internal/users/chat-validation")
    void validateChatUsers(@RequestBody ChatUserValidationRequest request);

    @GetMapping("/api/v1/users/{userId}")
    UserProfileClientResponse getUserProfile(@PathVariable("userId") UUID userId);
}
