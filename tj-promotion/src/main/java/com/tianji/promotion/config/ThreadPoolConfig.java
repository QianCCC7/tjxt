package com.tianji.promotion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {
    /**
     * 异步生成兑换码的线程池
     */
    @Bean
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

    /**
     * 计算折扣方案的线程池
     */
    @Bean
    public Executor calculateDiscountExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 1. 核心线程数量
        executor.setCorePoolSize(3);
        // 2. 线程池最大线程数量
        executor.setMaxPoolSize(6);
        // 3. 任务队列大小
        executor.setQueueCapacity(300);
        // 4. 线程名称
        executor.setThreadNamePrefix("discount-solution-calculator-");
        // 5. 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy() );
        executor.initialize();
        return executor;
    }
}
