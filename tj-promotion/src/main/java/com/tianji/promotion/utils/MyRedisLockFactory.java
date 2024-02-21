package com.tianji.promotion.utils;

import com.tianji.common.exceptions.BizIllegalException;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.tianji.promotion.utils.MyRedisLockType.*;

@Component
public class MyRedisLockFactory {
    private final RedissonClient redissonClient;
    private final Map<MyRedisLockType, Function<String, RLock>> lockHandlers;

    public MyRedisLockFactory(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
        this.lockHandlers = new HashMap<>(MyRedisLockType.values().length);
        this.lockHandlers.put(RE_ENTRANT_LOCK, redissonClient::getLock);
        this.lockHandlers.put(FAIR_LOCK, redissonClient::getFairLock);
        this.lockHandlers.put(READ_LOCK, name -> redissonClient.getReadWriteLock(name).readLock());
        this.lockHandlers.put(WRITE_LOCK, name -> redissonClient.getReadWriteLock(name).writeLock());
    }

    public RLock getLock(MyRedisLockType lockType, String name){
        return lockHandlers.get(lockType).apply(name);
    }
}
