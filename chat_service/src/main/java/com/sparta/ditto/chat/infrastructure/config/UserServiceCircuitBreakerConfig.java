package com.sparta.ditto.chat.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserServiceCircuitBreakerConfig {

    public static final String CHAT_VALIDATION_CIRCUIT_BREAKER =
            "userServiceChatValidation";

    @Bean
    public CircuitBreaker userServiceChatValidationCircuitBreaker(
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return circuitBreakerRegistry.circuitBreaker(CHAT_VALIDATION_CIRCUIT_BREAKER);
    }
}
