package com.bticketing.appqueue.service;

import com.bticketing.appqueue.util.TokenUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueueService {

    private static final String QUEUE_KEY = "userQueue";
    private static final String GROUP_KEY_PREFIX = "group-";
    private static final String REDIRECT_COUNT_KEY_PREFIX = "redirectCount-";
    private static final String USER_READY_KEY_PREFIX = "userReady-";
    private static final int MAX_QUEUE_SIZE = 500;
    private static final int GROUP_SIZE = 200;

    private final RedisTemplate<String, Object> redisTemplate;
    private int currentGroup = 1;

    public QueueService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 사용자 대기열 진입 처리
    public String handleUserEntry(String userToken) {
        if (userToken == null || userToken.isEmpty()) {
            userToken = TokenUtil.generateUserToken();
        }

        Long queueSize = redisTemplate.opsForList().size(QUEUE_KEY);

        // 500명 미만이면 바로 리다이렉트
        if (queueSize != null && queueSize < MAX_QUEUE_SIZE) {
            markUserAsReadyToRedirect(userToken);
            return "/seats/sections";
        }

        // 현재 그룹 키 생성 및 그룹 관리
        String groupKey = GROUP_KEY_PREFIX + currentGroup;
        Long groupSize = redisTemplate.opsForList().size(groupKey);

        // 그룹 사이즈가 200명에 도달하면 다음 그룹으로 전환
        if (groupSize != null && groupSize >= GROUP_SIZE) {
            currentGroup++;
            groupKey = GROUP_KEY_PREFIX + currentGroup;
        }

        // 사용자 대기열 및 현재 그룹에 추가
        redisTemplate.opsForList().rightPush(QUEUE_KEY, userToken);
        redisTemplate.opsForList().rightPush(groupKey, userToken);

        // 대기열 그룹 처리 메서드 호출
        processQueueGroup();

        return "addedToQueue";
    }

    // 그룹별 대기열 처리 메서드
    public void processQueueGroup() {
        String groupKey = GROUP_KEY_PREFIX + currentGroup;
        String redirectCountKey = REDIRECT_COUNT_KEY_PREFIX + currentGroup;
        Long groupSize = redisTemplate.opsForList().size(groupKey);

        // 현재 그룹이 존재하고, 그룹 사이즈가 200명 이상일 경우 처리
        if (groupSize != null && groupSize >= GROUP_SIZE) {
            for (int i = 0; i < GROUP_SIZE; i++) {
                String userToken = (String) redisTemplate.opsForList().leftPop(groupKey);
                if (userToken != null) {
                    markUserAsReadyToRedirect(userToken);

                    // Atomic Counter 증가
                    Long redirectCount = redisTemplate.opsForValue().increment(redirectCountKey);

                    // 모든 사용자가 리다이렉트되면 그룹 키 삭제
                    if (redirectCount != null && redirectCount >= GROUP_SIZE) {
                        redisTemplate.delete(groupKey);
                        redisTemplate.delete(redirectCountKey);
                        System.out.println("Deleted group key and redirect counter: " + groupKey);
                    }
                }
            }

            // 다음 그룹 처리
            currentGroup++;
            processQueueGroup();
        }
    }

    // 사용자 리다이렉트 상태 업데이트
    public void markUserAsReadyToRedirect(String userToken) {
        // Atomic Set: userToken:ready 키를 true로 설정
        redisTemplate.opsForValue().set(USER_READY_KEY_PREFIX + userToken, true);
    }

    // 사용자 리다이렉트 상태 확인
    public boolean isUserReadyToRedirect(String userToken) {
        Boolean isReady = (Boolean) redisTemplate.opsForValue().get(USER_READY_KEY_PREFIX + userToken);
        return Boolean.TRUE.equals(isReady);
    }
}
