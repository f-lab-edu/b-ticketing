package com.bticketing.appqueue.service;

import com.bticketing.appqueue.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RedisQueueService {

    private static final String QUEUE_KEY = "active_queue";
    private static final String VIP_KEY_PREFIX = "vip:";
    private static final long WAIT_TIME = 5000L; // 개별 대기 시간 (5초)
    private static final int MAX_ALLOWED_IN_QUEUE = 100; // 최대 100명씩 처리
    private static final long INTERVAL_TIME = 2000L; // 2초 간격

    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    @Autowired
    public RedisQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 임시 사용자 토큰 발급
    public String generateGuestToken() {
        return TokenUtil.generateUserToken();
    }

    public void addUserToQueue(String userToken) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(QUEUE_KEY, userToken, System.currentTimeMillis());

        scheduler.schedule(this::updateCanProceedStatus, WAIT_TIME, TimeUnit.MILLISECONDS);
    }

    // 대기열 상위 100명씩 2초 간격으로 canProceed 상태 업데이트
    public void updateCanProceedStatus() {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Set<Object> topUsers = zSetOps.range(QUEUE_KEY, 0, MAX_ALLOWED_IN_QUEUE - 1);

        if (topUsers != null) {
            int index = 0;
            for (Object user : topUsers) {
                final String userKey = "user_status:" + user;

                scheduler.schedule(() -> {
                    redisTemplate.opsForHash().put(userKey, "canProceed", "true");
                    zSetOps.remove(QUEUE_KEY, user);
                }, index * INTERVAL_TIME, TimeUnit.MILLISECONDS);

                index++;
            }
        }
    }

    public boolean isUserVIP(String userId) {
        String vipKey = VIP_KEY_PREFIX + userId;
        return redisTemplate.hasKey(vipKey);
    }

}
