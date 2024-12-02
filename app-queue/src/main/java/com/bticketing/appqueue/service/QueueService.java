package com.bticketing.appqueue.service;

import com.bticketing.appqueue.repository.QueueRepository;
import com.bticketing.appqueue.util.TokenUtil;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class QueueService {

    private static final Logger logger = LoggerFactory.getLogger(QueueService.class);

    private static final String QUEUE_KEY = "userQueue";
    private static final String GROUP_KEY_PREFIX = "group-";
    private static final String USER_READY_KEY_PREFIX = "userReady-";
    private static final String CURRENT_GROUP_KEY = "currentGroup";

    private static final int MAX_QUEUE_SIZE = 1000;
    private static final int GROUP_SIZE = 120;
    private final AtomicInteger cachedCurrentGroup = new AtomicInteger(1);

    private final QueueRepository queueRepository;

    public QueueService(QueueRepository queueRepository) {
        this.queueRepository = queueRepository;
    }

    // 현재 그룹 조회
    protected int getCurrentGroup() {
        int currentGroup = cachedCurrentGroup.get();
        Integer redisGroup = (Integer) queueRepository.getValue(CURRENT_GROUP_KEY);
        if (redisGroup != null && redisGroup > currentGroup) {
            cachedCurrentGroup.set(redisGroup);
            return redisGroup;
        }
        return currentGroup;
    }

    // 현재 그룹 업데이트
    protected void updateCurrentGroup(int newGroup) {
        try {
            queueRepository.setValue(CURRENT_GROUP_KEY, newGroup);
            cachedCurrentGroup.set(newGroup);
            logger.info("현재 그룹 번호가 {}로 업데이트 되었습니다.", newGroup);
        } catch (Exception e) {
            logger.error("현재 그룹 번호 업데이트 실패", e);
        }
    }

    // 사용자 진입 처리
    public String handleUserEntry(String userToken) {
        if (userToken == null || userToken.isBlank()) {
            userToken = TokenUtil.generateUserToken();
        }

        Long queueSize = queueRepository.incrementValue(QUEUE_KEY + "-size");
        if (queueSize != null && queueSize < MAX_QUEUE_SIZE) {
            queueRepository.setValueWithTTL(USER_READY_KEY_PREFIX + userToken, true, Duration.ofMinutes(10));
            return "/seats/sections";
        }

        int currentGroup = getCurrentGroup();
        if (!acquireLockWithSharding("lock:group", currentGroup, Duration.ofSeconds(5))) {
            logger.warn("그룹 {}에 사용자 추가를 위한 락 획득에 실패했습니다.", currentGroup);
            return "error";
        }

        try {
            String groupKey = GROUP_KEY_PREFIX + currentGroup;
            if (queueRepository.getListLength(groupKey) >= GROUP_SIZE) {
                updateCurrentGroup(currentGroup + 1);
                groupKey = GROUP_KEY_PREFIX + (currentGroup + 1);
            }

            queueRepository.pushToList(groupKey, userToken);
            queueRepository.setValueWithTTL(USER_READY_KEY_PREFIX + userToken, true, Duration.ofMinutes(10));
            logger.info("사용자 {}가 {} 그룹에 추가되었습니다.", userToken, groupKey);
        } finally {
            queueRepository.releaseLock("lock:group-" + currentGroup);
        }

        return "addedToQueue?userToken=" + userToken;
    }

    // 대기열 그룹 처리
    public void processQueueGroup() {
        int currentGroup = getCurrentGroup();
        String lockKey = "lock:processQueueGroup-" + currentGroup;

        if (!acquireLockWithSharding(lockKey, currentGroup, Duration.ofSeconds(5))) {
            logger.warn("그룹 {}의 대기열 처리를 위한 락 획득에 실패했습니다.", currentGroup);
            return;
        }

        try {
            String groupKey = GROUP_KEY_PREFIX + currentGroup;
            List<String> userTokens = queueRepository.popMultipleFromList(groupKey, GROUP_SIZE);
            if (userTokens.isEmpty()) {
                logger.warn("그룹 {}에서 사용자 토큰을 찾을 수 없습니다.", currentGroup);
                return;
            }

            // 사용자 데이터를 Redis에 저장
            for (String token : userTokens) {
                queueRepository.setValueWithTTL(USER_READY_KEY_PREFIX + token, true, Duration.ofMinutes(10));
            }
            updateCurrentGroup(currentGroup + 1);
        } finally {
            queueRepository.releaseLock(lockKey);
        }
    }

    // Sharded Lock 획득 메서드
    public boolean acquireLockWithSharding(String lockKeyPrefix, int groupId, Duration lockDuration) {
        String lockKey = lockKeyPrefix + "-" + groupId;
        int maxRetries = 5;
        int baseDelay = 50;

        for (int i = 0; i < maxRetries; i++) {
            if (queueRepository.acquireLock(lockKey, lockDuration)) {
                return true;
            }
            try {
                Thread.sleep(baseDelay * i);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        logger.warn("그룹 {}에 대해 {}회 재시도 후 락 획득에 실패했습니다.", groupId, maxRetries);
        return false;
    }

    // 사용자 리다이렉트 상태 확인
    public boolean isUserReadyToRedirect(String userToken) {
        Boolean isReady = (Boolean) queueRepository.getValue(USER_READY_KEY_PREFIX + userToken);
        return Boolean.TRUE.equals(isReady);
    }
}


