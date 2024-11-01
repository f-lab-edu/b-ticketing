package com.bticketing;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class RedisConnectionTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    public void testRedisConnection() {
        // 간단한 키-값 저장 테스트
        ValueOperations<String, String> valueOperations = redisTemplate.opsForValue();
        valueOperations.set("testKey", "testValue");

        // 저장한 값을 Redis에서 조회하여 확인
        String value = valueOperations.get("testKey");
        assertThat(value).isEqualTo("testValue"); // "testValue"가 반환되면 Redis 연결 성공
    }
}
