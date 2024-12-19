package com.bticketing.main.repository.redis;
import com.bticketing.main.service.SeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Repository
public class SeatRedisRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    public SeatRedisRepository(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquireLock(String key, String value, long ttlInSeconds) {
        // Redis의 SETNX 명령과 EXPIRE를 결합한 동작을 수행
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, ttlInSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);
    }


    public void releaseLock(String key) {
        // 락 해제
        redisTemplate.delete(key);
    }


    public String getSeatStatus(String key) {
        // Redis에서 좌석 상태 가져오기
        return (String) redisTemplate.opsForValue().get(key);
    }


    public void setSeatStatus(String key, String value, long ttlInSeconds) {
        // Redis에 좌석 상태 저장
        redisTemplate.opsForValue().set(key, value, ttlInSeconds, TimeUnit.SECONDS);
    }

    // 비동기 락 획득
    public CompletableFuture<Boolean> acquireLockAsync(String key, String value, long ttlInSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, value, ttlInSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(success);
        });
    }

    // 비동기 락 해제
    public CompletableFuture<Void> releaseLockAsync(String key) {
        return CompletableFuture.runAsync(() -> redisTemplate.delete(key));
    }

    // 비동기 락 획득 및 작업 실행
    public <T> CompletableFuture<T> executeWithLockAsync(String lockKey, long ttlInSeconds, Supplier<CompletableFuture<T>> action) {
        return acquireLockAsync(lockKey, "LOCKED", ttlInSeconds)
                .thenCompose(acquired -> {
                    if (!acquired) {
                        return CompletableFuture.failedFuture(new RuntimeException("다른 작업이 진행 중입니다. 잠시 후 다시 시도해주세요."));
                    }
                    return action.get()
                            .whenComplete((result, ex) -> releaseLockAsync(lockKey)); // 작업 완료 후 락 해제 (성공/실패 모두)
                });
    }

    public Map<String, String> getAllReservedSeats(int scheduleId) {
        String keyPattern = "seat:" + scheduleId + ":*";
        Set<String> keys = redisTemplate.keys(keyPattern); // Redis에서 해당 패턴에 맞는 모든 키 조회
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }

        // 모든 키의 상태를 조회하여 Map으로 반환
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        Map<String, String> result = new HashMap<>();
        int index = 0;
        for (String key : keys) {
            result.put(key, (String) values.get(index++));
        }
        return result;
    }

    public List<Integer> getAvailableSeatIds(int scheduleId) {
        String keyPattern = "seat:" + scheduleId + ":*";
        Set<String> keys = redisTemplate.keys(keyPattern); // 패턴으로 Redis에서 키 조회
        List<Integer> availableSeats = new ArrayList<>();

        if (keys != null) {
            for (String key : keys) {
                String status = (String) redisTemplate.opsForValue().get(key); // 상태 조회
                if ("AVAILABLE".equals(status)) {
                    String seatId = key.split(":")[2]; // 키에서 seatId 추출
                    availableSeats.add(Integer.parseInt(seatId));
                }
            }
        }
        return availableSeats;
    }

    public void clear() {
        redisTemplate.getConnectionFactory().getConnection().flushDb();
    }

    // Redis에 상태 저장
    public void setSeatStatus(String key, String status) {
        redisTemplate.opsForValue().set(key, status);
    }

    public Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisConnection connection) -> {
            Set<String> keys = new HashSet<>();
            Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(100).build());

            while (cursor.hasNext()) {
                keys.add(new String(cursor.next()));
            }
            return keys;
        });
    }




}
