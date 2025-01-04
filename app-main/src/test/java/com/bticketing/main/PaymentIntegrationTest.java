package com.bticketing.main;

import com.bticketing.main.repository.redis.PaymentRedisRepository;
import com.bticketing.main.service.PaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.TimeUnit;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {"payment-requested", "payment-completed"})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.redis.host=localhost",
        "spring.redis.port=6380"
})
public class PaymentIntegrationTest {

    @Autowired
    private PaymentRedisRepository paymentRedisRepository;

    @Autowired
    private PaymentService paymentService;

    @AfterEach
    void cleanupRedis() {
        // Redis에서 테스트 중 생성된 상태와 메시지 데이터를 정리합니다.
        paymentRedisRepository.savePaymentStatus("payment:status:testRequestId", null);
        paymentRedisRepository.savePaymentMessage("payment:message:testRequestId", null);
    }

    @Test
    void paymentFlowIntegrationTest() {
        // Given: 테스트 입력 데이터 준비
        String requestId = "testRequestId"; // 결제 요청 ID
        int reservationId = 1;             // 예약 ID
        double amount = 100.0;             // 결제 금액

        // When: 비동기 결제 처리 시작
        paymentService.processPaymentAsync(requestId, reservationId, amount);

        // Then
        // 1. Kafka Producer 메시지 처리 및 Redis "PENDING" 상태 확인
        await()
                .atMost(15, TimeUnit.SECONDS)
                .until(() -> {
                    String status = paymentRedisRepository.getPaymentStatus("payment:status:" + requestId);
                    System.out.println("PENDING Status: " + status); // 상태 확인 로그
                    return "PENDING".equals(status); // 상태가 "PENDING"인지 확인
                });

        // 2. Kafka Listener 메시지 처리 및 Redis "COMPLETED" 상태 확인
        await()
                .atMost(15, TimeUnit.SECONDS)
                .until(() -> {
                    String status = paymentRedisRepository.getPaymentStatus("payment:status:" + requestId);
                    System.out.println("COMPLETED Status: " + status); // 상태 확인 로그
                    return "COMPLETED".equals(status); // 상태가 "COMPLETED"인지 확인
                });

        // 3. Redis에서 결제 완료 메시지 확인
        String message = paymentRedisRepository.getPaymentMessage("payment:message:" + requestId);
        assertNotNull(message); // 메시지가 존재하는지 확인
        assertTrue(message.contains("결제 완료")); // 메시지 내용에 "결제 완료" 포함 여부 확인

        // 4. 최종적으로 Redis 상태가 "COMPLETED"인지 확인
        assertEquals("COMPLETED", paymentRedisRepository.getPaymentStatus("payment:status:" + requestId));
    }
}
