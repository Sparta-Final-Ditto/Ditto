package com.sparta.ditto.feed.infrastructure.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableFeignClients(basePackages = "com.sparta.ditto.feed.infrastructure.client")
public class FeignConfig {
}
