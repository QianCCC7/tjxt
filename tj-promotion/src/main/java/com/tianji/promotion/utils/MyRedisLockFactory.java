package com.tianji.promotion.utils;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import static com.tianji.promotion.utils.MyRedisLockType.*;

@Component
public class MyRedisLockFactory {
    private final Map<MyRedisLockType, Function<String, RLock>> lockHandlers;

    public MyRedisLockFactory(RedissonClient redissonClient) {
        // 只有当Key是枚举类型时可以使用EnumMap，其底层不是hash表，而是简单的数组。
        // 由于枚举项数量固定，因此这个数组长度就等于枚举项个数，然后按照枚举项序号作为角标依次存入数组。这样就能根据枚举项序号作为角标快速定位到数组中的数据。
        this.lockHandlers = new EnumMap<>(MyRedisLockType.class);
        this.lockHandlers.put(RE_ENTRANT_LOCK, redissonClient::getLock);
        this.lockHandlers.put(FAIR_LOCK, redissonClient::getFairLock);
        this.lockHandlers.put(READ_LOCK, name -> redissonClient.getReadWriteLock(name).readLock());
        this.lockHandlers.put(WRITE_LOCK, name -> redissonClient.getReadWriteLock(name).writeLock());
    }

    public RLock getLock(MyRedisLockType lockType, String name){
        return lockHandlers.get(lockType).apply(name);
    }
}
