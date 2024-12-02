package com.bticketing.appqueue;

import com.bticketing.appqueue.service.QueueService;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class QueueLoadTest {

    private static final Logger logger = LoggerFactory.getLogger(QueueLoadTest.class);

    @Autowired
    private QueueService queueService;

    private static final int TOTAL_USERS = 2000;
    private static final int THREAD_POOL_SIZE = 100;
    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        System.out.println("테스트 환경 설정 완료.");
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
        System.out.println("테스트 환경 정리 완료.");
    }

    @Test
    @DisplayName("QueueService 부하 테스트 - 동시 사용자 진입 및 그룹 처리 (토큰 관리 추가)")
    void testConcurrentUserEntryWithTokenManagement() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(TOTAL_USERS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        ConcurrentHashMap<Integer, String> userTokens = new ConcurrentHashMap<>();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < TOTAL_USERS; i++) {
            final int userId = i;
            executorService.submit(() -> {
                try {
                    // 처음 요청에서 토큰을 생성하고 저장
                    String response = queueService.handleUserEntry(null);
                    String userToken = response.startsWith("addedToQueue?userToken=")
                            ? response.split("=")[1]
                            : null;

                    if (userToken != null) {
                        userTokens.put(userId, userToken);
                    }

                    // 생성된 토큰으로 두 번째 요청 테스트
                    String statusResponse = queueService.handleUserEntry(userTokens.get(userId));

                    if ("/seats/sections".equals(statusResponse) || statusResponse.startsWith("addedToQueue?userToken=")) {
                        successCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                        System.err.println("Unexpected response: " + statusResponse);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    System.err.println("QueueService 접근 오류: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        logger.info("총 실행 시간(ms): " + totalTime);
        logger.info("성공한 요청 수: " + successCount.get());
        logger.info("실패한 요청 수: " + errorCount.get());
        logger.info("성공률(%): " + (successCount.get() * 100.0 / TOTAL_USERS));

        // 성공률이 90% 이상인지 검증
        assertTrue(successCount.get() >= (TOTAL_USERS * 0.9), "성공률이 90% 미만입니다.");
    }

    // 응답에서 userToken 추출 메서드
    private String extractUserToken(String response) {
        if (response != null && response.startsWith("addedToQueue?userToken=")) {
            return response.split("=")[1];
        }
        return null;
    }

    @Test
    @DisplayName("QueueService 그룹 처리 부하 테스트")
    void testConcurrentProcessQueueGroup() throws InterruptedException {
        int totalGroups = 50;
        CountDownLatch latch = new CountDownLatch(totalGroups);
        AtomicInteger processedGroups = new AtomicInteger(0);

        for (int i = 0; i < totalGroups; i++) {
            executorService.submit(() -> {
                try {
                    queueService.processQueueGroup();
                    processedGroups.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("processQueueGroup 오류: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        logger.info("처리된 그룹 수: " + processedGroups.get());

        // 모든 그룹이 정상적으로 처리되었는지 검증
        assertTrue(processedGroups.get() == totalGroups, "모든 그룹이 처리되지 않았습니다.");
    }
}
