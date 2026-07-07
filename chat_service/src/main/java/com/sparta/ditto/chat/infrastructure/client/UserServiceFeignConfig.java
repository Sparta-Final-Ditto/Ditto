package com.sparta.ditto.chat.infrastructure.client;

import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UserServiceFeignConfig {

    @Bean
    public Request.Options userServiceRequestOptions() {
        return new Request.Options(
                1,
                TimeUnit.SECONDS,
                2,
                TimeUnit.SECONDS,
                true
        );
    }

    @Bean
    public Retryer userServiceRetryer() {
        return new Retryer.Default(
                100,
                TimeUnit.MILLISECONDS.toMillis(300),
                2
        );
    }
}
