package com.bticketing.appqueue.service;

import com.bticketing.appqueue.util.RedisKeys;
import com.bticketing.appqueue.util.TokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RedisQueueService {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueueService.class);
    private static final long WAIT_TIME = 5000L; // 개별 대기 시간 (5초)
    private static final int MAX_ALLOWED_IN_QUEUE = 100; // 최대 100명씩 처리
    private static final long INTERVAL_TIME = 2000L; // 2초 간격

    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final SseService sseService; // SSE 전송을 위한 SseService 주입

    @Autowired
    public RedisQueueService(RedisTemplate<String, Object> redisTemplate, SseService sseService) {
        this.redisTemplate = redisTemplate;
        this.sseService = sseService;
    }

    // 자정에 실행되는 스케줄러: active_queue의 사용자 수 기록 후 초기화
    @Scheduled(cron = "0 0 0 * * *")
    public void resetQueueScore() {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Long totalUsersToday = zSetOps.size(RedisKeys.QUEUE_KEY);

        if (totalUsersToday != null) {
            LocalDate today = LocalDate.now();
            redisTemplate.opsForHash().put(RedisKeys.DAILY_STATS_KEY, today.toString(), totalUsersToday.toString());
            logger.info("{}: {}", today, totalUsersToday);
        }

        try {
            redisTemplate.delete(RedisKeys.QUEUE_KEY);
            logger.info("active_queue 데이터 삭제");
        } catch (Exception e) {
            logger.error("active_queue 데이터 삭제 실패.", e);
        }
    }

    // 임시 사용자 토큰 발급
    public String generateGuestToken() {
        return TokenUtil.generateUserToken();
    }

    public void addUserToQueue(String userToken) {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        zSetOps.add(RedisKeys.QUEUE_KEY, userToken, System.currentTimeMillis());

        scheduler.schedule(this::updateCanProceedStatus, WAIT_TIME, TimeUnit.MILLISECONDS);
    }

    // 대기열 상위 100명씩 2초 간격으로 canProceed 상태 업데이트
    public void updateCanProceedStatus() {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Set<Object> topUsers = zSetOps.range(RedisKeys.QUEUE_KEY, 0, MAX_ALLOWED_IN_QUEUE - 1);

        if (topUsers != null) {
            int index = 0;
            for (Object user : topUsers) {
                final String userKey = RedisKeys.getUserStatusKey((String) user);
                scheduler.schedule(() -> {
                    redisTemplate.opsForHash().put(userKey, "canProceed", "true");
                    zSetOps.remove(RedisKeys.QUEUE_KEY, user);

                    // SSE 이벤트 전송을 SseService로 위임
                    sseService.sendEvent((String) user, "queueStatus", "Proceed to /seats/sections");
                }, index * INTERVAL_TIME, TimeUnit.MILLISECONDS);

                index++;
            }
        }
    }

    public boolean isUserVIP(String userId) {
        String vipKey = RedisKeys.getVIPKey(userId);
        return redisTemplate.hasKey(vipKey);
    }
}
