package com.bticketing.main.repository.redis;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Repository
public class SeatRedisRepository implements RedisRepository{

    private final RedisTemplate<String, Object> redisTemplate;

    public SeatRedisRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean acquireLock(String key, String value, long ttlInSeconds) {
        // Redis의 SETNX 명령과 EXPIRE를 결합한 동작을 수행
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, ttlInSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void releaseLock(String key) {
        // 락 해제
        redisTemplate.delete(key);
    }

    @Override
    public String getSeatStatus(String key) {
        // Redis에서 좌석 상태 가져오기
        return (String) redisTemplate.opsForValue().get(key);
    }

    @Override
    public void setSeatStatus(String key, String value, long ttlInSeconds) {
        // Redis에 좌석 상태 저장
        redisTemplate.opsForValue().set(key, value, ttlInSeconds, TimeUnit.SECONDS);
    }

    @Override
    public <T> T executeWithLock(String lockKey, long ttlInSeconds, Supplier<T> action) {
        boolean lockAcquired = acquireLock(lockKey, "LOCKED", ttlInSeconds);
        if (!lockAcquired) {
            throw new RuntimeException("다른 작업이 진행 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            return action.get(); // 람다로 전달받은 작업 실행
        } finally {
            releaseLock(lockKey); // 작업 후 락 해제
        }
    }


}
