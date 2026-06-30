package com.sparta.ditto.chat.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class ChatAsyncConfig {

    private static final int NOTIFICATION_CORE_POOL_SIZE = 1;
    private static final int NOTIFICATION_MAX_POOL_SIZE = 2;
    private static final int NOTIFICATION_QUEUE_CAPACITY = 5000;

    @Bean
    public Executor chatNotificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(NOTIFICATION_CORE_POOL_SIZE);
        executor.setMaxPoolSize(NOTIFICATION_MAX_POOL_SIZE);
        executor.setQueueCapacity(NOTIFICATION_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("chat-notification-");
        executor.initialize();
        return executor;
    }
}
