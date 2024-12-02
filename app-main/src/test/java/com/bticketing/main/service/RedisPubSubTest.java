package com.bticketing.main.service;

import com.bticketing.main.messaging.RedisPubSubscriber;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class RedisPubSubTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisPubSubscriber redisPubSubscriber; // Redis 메시지 처리 클래스

    @Test
    void testRedisPublishAndSubscribe() {
        // 메시지 발행
        redisTemplate.convertAndSend("seat:updates", "{\"seatId\":1,\"status\":\"SELECTED\"}");

        // 메시지가 처리되었는지 검증
        redisPubSubscriber.onMessage("{\"seatId\":1,\"status\":\"SELECTED\"}", "seat:updates");
    }
}
