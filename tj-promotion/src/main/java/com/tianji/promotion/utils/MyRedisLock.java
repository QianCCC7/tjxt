package com.tianji.promotion.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MyRedisLock {
    String name();// 锁名称

    long waitTime() default 1;// 获取锁的等待时间

    long leaseTime() default -1;// 锁超时释放时间，设置为-1就仍然使用Redisson的默认值

    TimeUnit unit() default TimeUnit.SECONDS;// 时间单位
}
