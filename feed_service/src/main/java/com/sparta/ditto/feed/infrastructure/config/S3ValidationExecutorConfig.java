package com.sparta.ditto.feed.infrastructure.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * S3 객체 존재 검증 전용 Executor 설정.
 *
 * <p>게시글 생성 시 미디어 존재 확인(S3 HeadObject)은 블로킹 네트워크 I/O다. 이를 executor 지정 없이
 * {@code CompletableFuture.supplyAsync}로 실행하면 JVM 전역 공유 풀({@code ForkJoinPool.commonPool})의
 * 워커를 점유해, S3 지연 시 parallel stream 등 앱 전역 병렬 작업과 경합한다. 이를 막기 위해 전용
 * 스레드 풀로 격리한다.</p>
 *
 * <p>게시글당 미디어는 최대 6개(이미지 5 + 영상 1)이므로 작은 고정 풀로 충분하다.
 * {@link ThreadPoolTaskExecutor}는 Spring 빈 생명주기에 따라 컨텍스트 종료 시 graceful shutdown된다.</p>
 */
@Configuration
public class S3ValidationExecutorConfig {

    public static final String S3_VALIDATION_EXECUTOR = "s3ValidationExecutor";

    @Bean(S3_VALIDATION_EXECUTOR)
    public Executor s3ValidationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("s3-validation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}