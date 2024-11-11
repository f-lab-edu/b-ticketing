package com.bticketing.appqueue.service;

import com.bticketing.appqueue.util.RedisKeys;
import com.bticketing.appqueue.util.TokenUtil;
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
public class RedisQueueListService {

    private static final Logger logger = LoggerFactory.getLogger(RedisQueueListService.class);
    private static final long WAIT_TIME = 5000L; // 개별 대기 시간 (5초)
    private static final int MAX_ALLOWED_IN_QUEUE = 100; // 최대 100명씩 처리
    private static final long INTERVAL_TIME = 2000L; // 2초 간격

    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    private final SseService sseService; // SSE 전송을 위한 SseService 주입

    @Autowired
    public RedisQueueListService(RedisTemplate<String, Object> redisTemplate, SseService sseService) {
        this.redisTemplate = redisTemplate;
        this.sseService = sseService;
    }

    // 자정에 실행되는 스케줄러: active_queue의 사용자 수 기록 후 초기화
    @Scheduled(cron = "0 0 0 * * *")
    public void resetQueue() {
        Long totalUsersToday = redisTemplate.opsForList().size(RedisKeys.LIST_QUEUE_KEY);

        if (totalUsersToday != null) {
            LocalDate today = LocalDate.now();
            redisTemplate.opsForHash().put(RedisKeys.DAILY_STATS_KEY, today.toString(), totalUsersToday.toString());
            logger.info("{}: {}", today, totalUsersToday);
        }

        try {
            redisTemplate.delete(RedisKeys.LIST_QUEUE_KEY);
            logger.info("active_queue 데이터 삭제");
        } catch (Exception e) {
            logger.error("active_queue 데이터 삭제 실패.", e);
        }
    }

    // 임시 사용자 토큰 발급
    public String generateGuestToken() {
        return TokenUtil.generateUserToken();
    }

    // 사용자 대기열에 추가 (List 구조 사용)
    public void addUserToQueue(String userToken) {
        redisTemplate.opsForList().leftPush(RedisKeys.LIST_QUEUE_KEY, userToken);
        scheduler.schedule(this::updateCanProceedStatus, WAIT_TIME, TimeUnit.MILLISECONDS);
    }

    // 대기열 상위 100명씩 2초 간격으로 canProceed 상태 업데이트
    public void updateCanProceedStatus() {
        List<Object> topUsers = redisTemplate.opsForList().range(RedisKeys.LIST_QUEUE_KEY, -MAX_ALLOWED_IN_QUEUE, -1);

        if (topUsers != null) {
            int index = 0;
            for (Object user : topUsers) {
                final String userKey = RedisKeys.getUserStatusKey((String) user);
                scheduler.schedule(() -> {
                    redisTemplate.opsForHash().put(userKey, "canProceed", "true");
                    redisTemplate.opsForList().remove(RedisKeys.LIST_QUEUE_KEY, 1, user);

                    // SSE 이벤트 전송을 SseService로 위임
                    sseService.sendEvent((String) user, "queueStatus", "Proceed to /seats/sections");
                }, index * INTERVAL_TIME, TimeUnit.MILLISECONDS);

                index++;
            }
        }
    }

    // 사용자 VIP 여부 확인
    public boolean isUserVIP(String userId) {
        String vipKey = RedisKeys.getVIPKey(userId);
        return redisTemplate.hasKey(vipKey);
    }
}
