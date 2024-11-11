package com.bticketing.appqueue.service;

import com.bticketing.appqueue.util.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PollingService {

    private static final Logger logger = LoggerFactory.getLogger(PollingService.class);
    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public PollingService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public String checkCanProceedStatus(String userToken) {
        String userKey = RedisKeys.getUserStatusKey(userToken);
        String canProceed = (String) redisTemplate.opsForHash().get(userKey, "canProceed");

        if ("true".equals(canProceed)) {
            logger.info("Polling: User '{}' can proceed.", userToken);
            return "Proceed to /seats/sections";
        }
        return "Waiting...";
    }
}
