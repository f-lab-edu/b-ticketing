package com.bticketing.appqueue;

import com.bticketing.appqueue.service.RedisQueueListService;
import com.bticketing.appqueue.service.RedisQueueService;
import com.bticketing.appqueue.service.SseService;
import com.bticketing.appqueue.util.RedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class QueueComparisonTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private RedisQueueListService redisQueueListService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SseService sseService;

    private RestTemplate restTemplate;

    @BeforeEach
    public void setup() {
        restTemplate = new RestTemplate();
        // Redis 초기화
        redisTemplate.delete(RedisKeys.ZSET_QUEUE_KEY);
        redisTemplate.delete(RedisKeys.LIST_QUEUE_KEY);
    }

    // ZSet 구조 성능 테스트
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testZSetPerformance() {
        int userCount = 1000;

        // 사용자 추가 시간 측정
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < userCount; i++) {
            redisQueueService.addUserToQueue("zsetUser" + i);
        }
        long endTime = System.currentTimeMillis();
        long additionTime = endTime - startTime;
        System.out.println("ZSet - 사용자 추가 시간: " + additionTime + " ms");

        // 상태 업데이트 시간 측정
        startTime = System.currentTimeMillis();
        redisQueueService.updateCanProceedStatus();
        endTime = System.currentTimeMillis();
        long updateTime = endTime - startTime;
        System.out.println("ZSet - 상태 업데이트 시간: " + updateTime + " ms");

        // 전체 시나리오 시간
        System.out.println("ZSet 구조 전체 테스트 소요 시간: " + (additionTime + updateTime) + " ms");
    }

    // List 구조 성능 테스트
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testListPerformance() {
        int userCount = 1000;

        // 사용자 추가 시간 측정
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < userCount; i++) {
            redisQueueListService.addUserToQueue("listUser" + i);
        }
        long endTime = System.currentTimeMillis();
        long additionTime = endTime - startTime;
        System.out.println("List - 사용자 추가 시간: " + additionTime + " ms");

        // 상태 업데이트 시간 측정
        startTime = System.currentTimeMillis();
        redisQueueListService.updateCanProceedStatus();
        endTime = System.currentTimeMillis();
        long updateTime = endTime - startTime;
        System.out.println("List - 상태 업데이트 시간: " + updateTime + " ms");

        // 전체 시나리오 시간
        System.out.println("List 구조 전체 테스트 소요 시간: " + (additionTime + updateTime) + " ms");
    }

    /*
    - Test 결과 -
    ZSet - 사용자 추가 시간: 160 ms
    ZSet - 상태 업데이트 시간: 12 ms
    ZSet 구조 전체 테스트 소요 시간: 172 ms
    List - 사용자 추가 시간: 82 ms
    List - 상태 업데이트 시간: 3 ms
    List 구조 전체 테스트 소요 시간: 85 ms
    * */


}
