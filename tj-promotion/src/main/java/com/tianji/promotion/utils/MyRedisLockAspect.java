package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class MyRedisLockAspect implements Ordered {
    private final RedissonClient redissonClient;

    @Around("@annotation(myRedisLock)")
    public Object tryLock(ProceedingJoinPoint joinPoint, MyRedisLock myRedisLock) throws Throwable {
        // 1. 创建锁对象
        RLock lock = redissonClient.getLock(myRedisLock.name());
        // 2. 尝试获取锁
        boolean hasLock = lock.tryLock(myRedisLock.waitTime(), myRedisLock.leaseTime(), myRedisLock.unit());
        // 3. 判断是否成功
        if (!hasLock) {
            // 3.1 失败
            throw new BizIllegalException("请求太频繁");
        }
        try {
            // 3.2 成功，执行业务
            return joinPoint.proceed();
        } finally {
            // 4. 释放锁
            lock.unlock();
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
