package com.bticketing.main.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AppConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "threadPoolTaskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // 기본 스레드 풀 크기 (I/O 바운드 작업이 많으므로 코어 수보다 크게 설정)
        executor.setMaxPoolSize(50); // 최대 스레드 풀 크기
        executor.setQueueCapacity(100); // 큐 크기
        executor.setThreadNamePrefix("MyExecutor-"); // 스레드 이름 접두사
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 거부 정책 (CallerRunsPolicy: 호출한 스레드에서 실행)
        executor.initialize();
        return executor;
    }

}
