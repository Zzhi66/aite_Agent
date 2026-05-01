package com.kama.jchatmind.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);// 核心线程数：4
        executor.setMaxPoolSize(10);// 最大线程数：10
        executor.setQueueCapacity(100);// 队列容量：100
        executor.setThreadNamePrefix("async-event-");// 线程名前缀：async-event-
        executor.initialize();// 初始化
        return executor;
    }
}
// 这个配置创建了一个 4~10 线程的线程池，专门用来异步执行 Agent 的 AI 推理任务，
// 避免阻塞 HTTP 请求。