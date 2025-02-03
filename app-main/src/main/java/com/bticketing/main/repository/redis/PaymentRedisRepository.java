package com.bticketing.main.repository.redis;

import com.bticketing.main.service.SeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaymentRedisRepository {

    private static final Logger logger = LoggerFactory.getLogger(PaymentRedisRepository.class);

    private static final String STATUS_KEY_PREFIX = "payment:status:";
    private static final String MESSAGE_KEY_PREFIX = "payment:message:";

    private final RedisTemplate<String, String> redisTemplate;

    public PaymentRedisRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void savePaymentStatus(String requestId, String status) {
        logger.info("Saving status to Redis: key={}, value={}", STATUS_KEY_PREFIX + requestId, status);
        redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, status);
    }

    public String getPaymentStatus(String requestId) {
        return redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + requestId);
    }

    public void savePaymentMessage(String requestId, String message) {
        logger.info("Saving message to Redis: key={}, value={}", MESSAGE_KEY_PREFIX + requestId, message);
            redisTemplate.opsForValue().set(MESSAGE_KEY_PREFIX + requestId, message);

    }

    public String getPaymentMessage(String requestId) {
        return redisTemplate.opsForValue().get(MESSAGE_KEY_PREFIX + requestId);
    }

    public void deletePaymentStatus(String key) {
        redisTemplate.opsForValue().getOperations().delete(key);
    }

    public void deletePaymentMessage(String key) {
        redisTemplate.opsForValue().getOperations().delete(key);
    }

}
