package com.bticketing.main.repository;

import java.util.function.Supplier;

public interface RedisRepository {
    boolean acquireLock(String key, String value, long ttlInSeconds);

    void releaseLock(String key);

    String getSeatStatus(String key);

    void setSeatStatus(String key, String value, long ttlInSeconds);

    <T> T executeWithLock(String lockKey, long ttlInSeconds, Supplier<T> action);
}
