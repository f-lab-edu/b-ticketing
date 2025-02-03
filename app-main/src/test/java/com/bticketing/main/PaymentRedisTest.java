package com.bticketing.main;

import com.bticketing.main.entity.Payment;
import com.bticketing.main.repository.redis.PaymentRedisRepository;
import com.bticketing.main.service.PaymentService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.util.AssertionErrors.assertEquals;

@SpringBootTest
public class PaymentRedisTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRedisRepository paymentRedisRepository;

    @BeforeEach
    void setup() {
        // Redis 초기화 (기존 테스트 데이터 제거)
        paymentRedisRepository.deletePaymentStatus("testRequestId");
        paymentRedisRepository.deletePaymentMessage("testRequestId");
    }

    @Test
    void testProcessPaymentWithRedis() {
        // Given
        String requestId = "testRequestId";
        int reservationId = 1;
        double amount = 100.0;

        // When
        Payment payment = paymentService.processPayment(requestId, reservationId, amount);

        // Then
        // Redis 상태 확인
        String status = paymentService.getPaymentStatus(requestId);
        String message = paymentService.getPaymentMessage(requestId);

        assertThat(status).isEqualTo("COMPLETED");
        assertThat(message).contains("결제 완료");
        assertThat(payment.getPaymentStatus()).isEqualTo("COMPLETED");
    }
}
