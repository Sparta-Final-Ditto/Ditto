package com.sparta.ditto.chat.config;

import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Slf4j
@Configuration
@EnableAsync
public class ChatAsyncConfig {

    // 알림은 응답 경로에서 분리된 후처리. 전송 채널 executor보다 작게 둬 자원 경쟁을 줄인다.
    // queue가 크면 큐가 안 차서 maxPool이 안 늘어나므로 작게(500) 둔다.
    private static final int NOTIFICATION_CORE_POOL_SIZE = 2;
    private static final int NOTIFICATION_MAX_POOL_SIZE = 4;
    private static final int NOTIFICATION_QUEUE_CAPACITY = 500;
    private static final int AWAIT_TERMINATION_SECONDS = 20;

    @Bean
    public Executor chatNotificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(NOTIFICATION_CORE_POOL_SIZE);
        executor.setMaxPoolSize(NOTIFICATION_MAX_POOL_SIZE);
        executor.setQueueCapacity(NOTIFICATION_QUEUE_CAPACITY);
        executor.setThreadNamePrefix("chat-notification-");
        // 알림은 best-effort라 포화 시 드롭 + 로그(전송이 우선).
        // CallerRuns(백프레셔)는 AFTER_COMMIT submit이 ack/broadcast보다 먼저라 응답을 막아 부적합.
        executor.setRejectedExecutionHandler((task, exec) ->
                log.warn("알림 executor 포화로 알림 드롭(메시지 전송은 정상). activeCount={}, queueSize={}",
                        exec.getActiveCount(), exec.getQueue().size()));
        // 종료 시 큐에 남은 알림 작업을 drain (graceful shutdown).
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }
}
