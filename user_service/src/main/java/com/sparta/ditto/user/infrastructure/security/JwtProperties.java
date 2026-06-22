package com.sparta.ditto.user.infrastructure.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        Duration accessTokenValidity,
        Duration refreshTokenValidity
) {
}
