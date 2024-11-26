package com.bticketing.appqueue.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

@Repository
public class RedisQueueRepository implements QueueRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisQueueRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Object getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public void setValue(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void setValueWithTTL(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    @Override
    public Long incrementValue(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    @Override
    public Long getListLength(String key) {
        return redisTemplate.opsForList().size(key);
    }

    @Override
    public void pushToList(String key, String value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    @Override
    public List<String> popMultipleFromList(String key, int count) {

        List<Object> results = redisTemplate.opsForList().range(key, 0, count - 1);

        if (results != null && !results.isEmpty()) {
            List<String> stringResults = results.stream()
                    .map(String::valueOf)
                    .toList();

            redisTemplate.opsForList().trim(key, count, -1);
            return stringResults;
        }
        return List.of();
    }

    @Override
    public boolean acquireLock(String key, Duration ttl) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "locked", ttl);
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void releaseLock(String key) {
        redisTemplate.delete(key);
    }





}
