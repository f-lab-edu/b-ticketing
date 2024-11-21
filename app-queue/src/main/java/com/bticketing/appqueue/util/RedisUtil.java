package com.bticketing.appqueue.util;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.SessionCallback;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@Component
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtil(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 값 가져오기
    public Object getValue(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 값 설정
    public void setValue(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    // 값 설정 (TTL 포함)
    public void setValueWithTTL(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    // 값 증가
    public Long incrementValue(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    // 리스트 길이 가져오기
    public Long getListLength(String key) {
        return redisTemplate.opsForList().size(key);
    }

    // 리스트에 값 추가
    public void rightPushToList(String key, String value) {
        redisTemplate.opsForList().rightPush(key, value);
    }

    // Redis에서 리스트에서 여러 값 꺼내기 메서드
    public List<String> leftPopMultipleFromList(String key, int count) {
        List<String> results = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                String value = (String) redisTemplate.opsForList().leftPop(key);
                if (value == null) {
                    break;
                }
                results.add(value);
            }
        } catch (Exception e) {
            System.err.println("Error during leftPopMultipleFromList: " + e.getMessage());
        }
        return results;
    }

    // 배치로 여러 값 설정 (MSET 사용)
    public void setMultipleValues(List<String> keys, Object value, Duration ttl) {
        Map<String, Object> valueMap = new HashMap<>();
        for (String key : keys) {
            valueMap.put(key, value);
        }
        redisTemplate.opsForValue().multiSet(valueMap);

        // TTL 설정
        for (String key : keys) {
            redisTemplate.expire(key, ttl);
        }
    }

    // 분산 락 획득
    public boolean acquireLock(String key, Duration ttl) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "locked", ttl);
        return Boolean.TRUE.equals(result);
    }

    // 분산 락 해제
    public void releaseLock(String key) {
        redisTemplate.delete(key);
    }


}
