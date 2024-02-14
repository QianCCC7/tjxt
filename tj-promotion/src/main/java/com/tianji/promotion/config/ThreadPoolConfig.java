package com.tianji.promotion.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {
    /**
     * 异步生成兑换码的线程池
     */
    public Executor generateExchangeCodeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1. 核心线程数量
        executor.setCorePoolSize(2);
        // 2. 线程池最大线程数量
        executor.setMaxPoolSize(5);
        // 3. 任务队列大小
        executor.setQueueCapacity(200);
        // 4. 线程名称
        executor.setThreadNamePrefix("exchange-code-handler-");
        // 5. 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
