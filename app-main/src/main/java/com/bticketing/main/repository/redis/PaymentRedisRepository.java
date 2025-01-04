package com.bticketing.main.repository.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRedisRepository {

    private static final String STATUS_KEY_PREFIX = "payment:status:";
    private static final String MESSAGE_KEY_PREFIX = "payment:message:";

    private final RedisTemplate<String, String> redisTemplate;

    public PaymentRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void savePaymentStatus(String requestId, String status) {
        if (status == null) {
            redisTemplate.delete(STATUS_KEY_PREFIX + requestId); // null이면 키를 삭제
        } else {
            redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, status);
        }
    }

    public String getPaymentStatus(String requestId) {
        return redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + requestId);
    }

    public void savePaymentMessage(String requestId, String message) {
        if (message == null) {
            redisTemplate.delete(MESSAGE_KEY_PREFIX + requestId); // null이면 키를 삭제
        } else {
            redisTemplate.opsForValue().set(MESSAGE_KEY_PREFIX + requestId, message);
        }
    }

    public String getPaymentMessage(String requestId) {
        return redisTemplate.opsForValue().get(MESSAGE_KEY_PREFIX + requestId);
    }
}
