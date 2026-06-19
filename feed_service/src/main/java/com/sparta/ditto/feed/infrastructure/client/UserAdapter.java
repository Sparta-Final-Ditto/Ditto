package com.sparta.ditto.feed.infrastructure.client;

import com.sparta.ditto.feed.domain.port.UserPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Component
public class UserAdapter implements UserPort {

    private final RestClient restClient;

    public UserAdapter(@Value("${app.user-service.base-url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @SuppressWarnings("unchecked")
    @Override
    public String getNickname(UUID userId) {
        try {
            Map<String, Object> response = restClient.get()
                    .uri("/users/{userId}", userId)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return null;
            }

            Object data = response.get("data");
            if (data instanceof Map<?, ?> dataMap) {
                return (String) dataMap.get("nickname");
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}