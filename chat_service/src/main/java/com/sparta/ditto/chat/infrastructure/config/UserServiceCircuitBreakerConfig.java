package com.sparta.ditto.chat.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserServiceCircuitBreakerConfig {

    public static final String CHAT_VALIDATION_CIRCUIT_BREAKER =
            "userServiceChatValidation";
    public static final String CHAT_PROFILE_CIRCUIT_BREAKER =
            "userServiceChatProfile";

    @Bean
    public CircuitBreaker userServiceChatValidationCircuitBreaker(
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return circuitBreakerRegistry.circuitBreaker(CHAT_VALIDATION_CIRCUIT_BREAKER);
    }

    @Bean
    public CircuitBreaker userServiceChatProfileCircuitBreaker(
            CircuitBreakerRegistry circuitBreakerRegistry
    ) {
        return circuitBreakerRegistry.circuitBreaker(CHAT_PROFILE_CIRCUIT_BREAKER);
    }
}
