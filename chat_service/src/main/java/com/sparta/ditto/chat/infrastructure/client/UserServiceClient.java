package com.sparta.ditto.chat.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
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
}
