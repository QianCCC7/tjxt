package com.tianji.promotion.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * redis实现分布式锁
 */
@RequiredArgsConstructor
public class RedisLock {
    private final String key;
    private final StringRedisTemplate redisTemplate;

    /**
     * 获取锁
     */
    public boolean tryLock(long expiredTime, TimeUnit timeUnit) {
        // 1. 获取线程名称
        String threadName = Thread.currentThread().getName();
        // 2. 获取锁
        Boolean suc = redisTemplate.opsForValue().setIfAbsent(key, threadName, expiredTime, timeUnit);
        // 3. 返回结果
        return suc != null && suc;
    }

    /**
     * 释放锁
     */
    public void unlock() {
        redisTemplate.delete(key);
    }
}
