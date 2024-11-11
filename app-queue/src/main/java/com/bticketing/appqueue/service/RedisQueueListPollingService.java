package com.bticketing.appqueue.service;

import com.bticketing.appqueue.util.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RedisQueueListPollingService {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueueListPollingService.class);
    private static final long WAIT_TIME = 5000L;
    private static final int MAX_ALLOWED_IN_QUEUE = 100;
    private static final long INTERVAL_TIME = 2000L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    @Autowired
    public RedisQueueListPollingService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 사용자를 대기열에 추가
    public void addUserToQueue(String userToken) {
        redisTemplate.opsForList().leftPush(RedisKeys.LIST_QUEUE_KEY, userToken);
        scheduler.schedule(this::updateCanProceedStatus, WAIT_TIME, TimeUnit.MILLISECONDS);
    }

    // 대기열의 상위 사용자 상태 업데이트
    public void updateCanProceedStatus() {
        List<Object> topUsers = redisTemplate.opsForList().range(RedisKeys.LIST_QUEUE_KEY, -MAX_ALLOWED_IN_QUEUE, -1);

        if (topUsers != null) {
            int index = 0;
            for (Object user : topUsers) {
                final String userKey = RedisKeys.getUserStatusKey((String) user);
                scheduler.schedule(() -> {
                    redisTemplate.opsForHash().put(userKey, "canProceed", "true");
                    redisTemplate.opsForList().remove(RedisKeys.LIST_QUEUE_KEY, 1, user);
                    logger.info("Polling - User '{}' 상태 업데이트: canProceed = true", user);
                }, index * INTERVAL_TIME, TimeUnit.MILLISECONDS);

                index++;
            }
        }
    }

    // 사용자 상태 조회 (Polling 요청 처리)
    public String getUserStatus(String userToken) {
        String userKey = RedisKeys.getUserStatusKey(userToken);
        String canProceed = (String) redisTemplate.opsForHash().get(userKey, "canProceed");

        if ("true".equals(canProceed)) {
            logger.info("Polling - User '{}' can proceed.", userToken);
            return "Proceed to /seats/sections";
        }
        return "Waiting...";
    }
}
