package com.bticketing.appqueue;

import com.bticketing.appqueue.service.RedisQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class QueueIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private RestTemplate restTemplate;

    @BeforeEach
    public void setup() {
        restTemplate = new RestTemplate();
        // Redis 초기화 (테스트 실행 전 상태 클리어)
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    // resetQueueScore() 메서드 테스트
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testResetQueueScore() {
        // 100명의 사용자 추가
        for (int i = 1; i <= 100; i++) {
            String userToken = "userToken" + i;
            redisQueueService.addUserToQueue(userToken);
        }

        // resetQueueScore() 메서드 실행
        redisQueueService.resetQueueScore();

        // 일일 통계 기록 확인
        String today = LocalDate.now().toString();
        String recordedCount = (String) redisTemplate.opsForHash().get("daily_queue_stats", today);
        assertThat(recordedCount).isEqualTo("100");

        // active_queue가 초기화되었는지 확인
        Long queueSize = redisTemplate.opsForZSet().size("active_queue");
        assertThat(queueSize).isZero();
    }

    // VIP 사용자의 접근 테스트
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testGetSeatList_VIPUser() {
        String vipToken = "vipUserToken";
        redisTemplate.opsForValue().set("vip:" + vipToken, "true");

        String url = "http://localhost:" + port + "/queue/seats?userToken=" + vipToken;
        String response = restTemplate.getForObject(url, String.class);
        assertThat(response).contains("VIP access granted to: " + vipToken);
    }

    // 일반 사용자의 대기열 추가 테스트
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testGetSeatList_NonVIPUser() {
        String userToken = "nonVipUserToken";
        String url = "http://localhost:" + port + "/queue/seats?userToken=" + userToken;

        String response = restTemplate.getForObject(url, String.class);
        assertThat(response).contains("User added to queue: " + userToken);

        Boolean isInQueue = redisTemplate.opsForZSet().rank("active_queue", userToken) != null;
        assertThat(isInQueue).isTrue();
    }

    // 비회원의 임시 토큰 발급 및 대기열 추가 테스트
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testGetSeatList_GuestUser() {
        String url = "http://localhost:" + port + "/queue/seats";

        String response = restTemplate.getForObject(url, String.class);
        assertThat(response).contains("Guest access granted with temporary token");

        // 임시 토큰이 Redis에 저장되었는지 확인
        String guestToken = response.split(": ")[1].split("\\.")[0];
        Boolean isInQueue = redisTemplate.opsForZSet().rank("active_queue", guestToken) != null;
        assertThat(isInQueue).isTrue();
    }


    // 일반 사용자의 자동 삭제 테스트
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testAutoRemovalOfRegularUser() {
        String userToken = "removableUserToken";
        String url = "http://localhost:" + port + "/queue/seats?userToken=" + userToken;

        restTemplate.getForObject(url, String.class);

        // 5초 후에 자동으로 대기열에서 삭제되는지 확인
        await().atMost(6, TimeUnit.SECONDS).untilAsserted(() -> {
            Boolean isInQueue = redisTemplate.opsForZSet().rank("active_queue", userToken) == null;
            assertThat(isInQueue).isTrue();
        });
    }

    // 100명 이상의 사용자가 대기열에 추가된 상태에서 상위 100명 상태 업데이트 테스트
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testStatusUpdate_For100PlusUsers() {
        String baseUrl = "http://localhost:" + port + "/queue/seats?userToken=userToken";

        for (int i = 1; i <= 150; i++) {
            restTemplate.getForObject(baseUrl + i, String.class);
        }

        // 상위 100명의 상태가 2초 간격으로 업데이트되는지 확인
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            int canProceedCount = 0;
            for (int i = 1; i <= 100; i++) {
                String status = (String) redisTemplate.opsForHash().get("user_status:userToken" + i, "canProceed");
                if ("true".equals(status)) {
                    canProceedCount++;
                }
            }
            assertThat(canProceedCount).isGreaterThan(0);
        });

        // 나머지 50명의 상태가 이후에 업데이트되는지 확인
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            int canProceedCount = 0;
            for (int i = 101; i <= 150; i++) {
                String status = (String) redisTemplate.opsForHash().get("user_status:userToken" + i, "canProceed");
                if ("true".equals(status)) {
                    canProceedCount++;
                }
            }
            assertThat(canProceedCount).isGreaterThan(0);
        });
    }
}
