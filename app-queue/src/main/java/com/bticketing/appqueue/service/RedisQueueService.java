package com.bticketing.appqueue.service;

import com.bticketing.appqueue.util.TokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class RedisQueueService {

    private static final String QUEUE_KEY = "active_queue";
    private static final String DAILY_STATS_KEY = "daily_queue_stats";
    private static final String VIP_KEY_PREFIX = "vip:";
    private static final long WAIT_TIME = 5000L; // 개별 대기 시간 (5초)
    private static final int MAX_ALLOWED_IN_QUEUE = 100; // 최대 100명씩 처리
    private static final long INTERVAL_TIME = 2000L; // 2초 간격

    private final RedisTemplate<String, Object> redisTemplate;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);

    private static final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Autowired
    public RedisQueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // SSE Emitter 추가 메서드
    public SseEmitter addSseEmitter(String userToken) {
        SseEmitter emitter = new SseEmitter();
        emitters.put(userToken, emitter);

        // SSE 연결이 종료되면 자동으로 제거
        emitter.onCompletion(() -> emitters.remove(userToken));
        emitter.onTimeout(() -> emitters.remove(userToken));

        return emitter;
    }

    // 자정에 실행되는 스케줄러: active_queue의 사용자 수 기록 후 초기화
    @Scheduled(cron = "0 0 0 * * *")
    public void resetQueueScore() {
        ZSetOperations<String, Object> zSetOps = redisTemplate.opsForZSet();
        Long totalUsersToday = zSetOps.size(QUEUE_KEY);

        if (totalUsersToday != null) {
            // 현재 날짜를 키로 일일 사용자 수 기록
            LocalDate today = LocalDate.now();
            redisTemplate.opsForHash().put(DAILY_STATS_KEY, today.toString(), totalUsersToday.toString());
        }

        // active_queue 초기화
        redisTemplate.delete(QUEUE_KEY);
        System.out.println("Queue scores reset at midnight, with today's user count recorded.");
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

                    // SSE 이벤트 전송
                    SseEmitter emitter = emitters.get(user);
                    if (emitter != null) {
                        try {
                            emitter.send(SseEmitter.event().name("queueStatus").data("Proceed to /seats/sections"));
                            emitter.complete();
                        } catch (Exception e) {
                            emitters.remove(user);
                        }
                    }
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
