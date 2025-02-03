package com.bticketing.main;

import com.bticketing.main.repository.redis.PaymentRedisRepository;
import com.bticketing.main.service.PaymentService;
import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=localhost:9092", // 로컬 Kafka 서버 설정
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.redis.host=localhost",
        "spring.redis.port=6380"
})
public class PaymentIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(PaymentIntegrationTest.class);

    @Autowired
    private PaymentRedisRepository paymentRedisRepository;

    @Autowired
    private PaymentService paymentService;

    @BeforeEach
    void setupEnvironment() {
        // Kafka 클러스터 상태 확인
        ensureKafkaTopicsExist(List.of("payment-requested", "payment-completed"));

        // Redis 초기화
        resetRedisKeys("testRequestId");
    }

    /**
     * Kafka 토픽 상태를 확인하고 존재하지 않을 경우 예외를 발생시킴.
     */
    private void ensureKafkaTopicsExist(List<String> topics) {
        try (AdminClient adminClient = AdminClient.create(Map.of("bootstrap.servers", "localhost:9092"))) {
            await().atMost(30, TimeUnit.SECONDS).until(() -> {
                try {
                    Map<String, ?> topicDescriptions = adminClient.describeTopics(topics).all().get();
                    return topicDescriptions != null && !topicDescriptions.isEmpty();
                } catch (Exception e) {
                    logger.error("Error while verifying Kafka topics: {}", e.getMessage(), e);
                    return false;
                }
            });
            logger.info("Kafka topics verified: {}", topics);
        } catch (Exception e) {
            throw new RuntimeException("Kafka topics verification failed", e);
        }
    }

    /**
     * Redis 키 초기화 로직.
     */
    private void resetRedisKeys(String requestId) {
            paymentRedisRepository.deletePaymentStatus(requestId);
            paymentRedisRepository.deletePaymentMessage(requestId);
    }

    @Test
    void paymentFlowIntegrationTest() {
        String requestId = "testRequestId";
        int reservationId = 1;
        double amount = 100.0;

        // 결제 처리 시작
        paymentService.processPaymentAsync(requestId, reservationId, amount);

        // 1. PENDING 상태 확인
        await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    String status = paymentRedisRepository.getPaymentStatus(requestId);
                    logger.info("PENDING Status in Redis: {}", status);
                    return "PENDING".equals(status);
                });

        // 2. COMPLETED 상태 확인
        await()
                .atMost(20, TimeUnit.SECONDS) // 대기 시간을 20초로 증가
                .until(() -> {
                    String status = paymentRedisRepository.getPaymentStatus(requestId);
                    logger.info("COMPLETED Status in Redis: {}", status);
                    return "COMPLETED".equals(status);
                });

        // 3. 결제 완료 메시지 확인
        await()
                .atMost(20, TimeUnit.SECONDS)
                .until(() -> {
                    String message = paymentRedisRepository.getPaymentMessage(requestId);
                    logger.info("Message in Redis: {}", message);
                    return message != null && message.contains("결제 완료");
                });

        // 4. 이메일 발송 검증
        await()
                .atMost(20, TimeUnit.SECONDS)
                .until(() -> {
                    String message = paymentRedisRepository.getPaymentMessage(requestId);
                    logger.info("Email Message Verification: {}", message);
                    return message != null && message.contains("결제 완료: ReservationId=" + reservationId);
                });
    }
}
