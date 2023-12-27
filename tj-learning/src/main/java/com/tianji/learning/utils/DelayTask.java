package com.tianji.learning.utils;

import lombok.Data;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Data
public class DelayTask<D> implements Delayed {

    private D data;// 任务中的数据
    private long deadlineNanos;// 任务到达该截止时间时才开始执行，单位为纳秒

    // 创建任务时，只需要指定任务的数据以及任务的延迟时间
    public DelayTask(D data, Duration delayTime) {
        this.data = data;
        this.deadlineNanos = System.nanoTime() + delayTime.toNanos();// 当前时间+任务延迟时间=任务开始执行的时间
    }

    // 计算当前任务的剩余延迟时间
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(Math.max(0, this.deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    // 用于比较执行顺序
    @Override
    public int compareTo(Delayed o) {
        long c = this.getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
        if (c > 0) return 1;// 大于0说明当前任务的剩余延迟时间较长，则排在后面
        else if (c < 0) return -1;
        return 0;
    }
}
