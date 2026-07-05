package com.sparta.ditto.notification.infrastructure.config;

import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * SSE 실시간 전송 전용 비동기 executor 및 스케줄링 설정. 느리거나 실패하는 클라이언트 전송이
 * Kafka 컨슈머 스레드/처리량에 영향을 주지 않도록 소형 bounded 풀로 격리한다. 큐가 포화되면
 * 전송을 버리고(discard) 로그만 남긴다. heartbeat 스케줄러 구동을 위해 스케줄링도 활성화한다.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("ssePushExecutor")
    public Executor ssePushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("sse-push-");
        executor.setRejectedExecutionHandler((runnable, poolExecutor) ->
                log.warn("SSE push executor 포화로 전송 폐기 - activeCount={}, queueSize={}",
                        poolExecutor.getActiveCount(), poolExecutor.getQueue().size()));
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}