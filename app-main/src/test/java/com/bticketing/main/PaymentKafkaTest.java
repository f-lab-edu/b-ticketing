package com.bticketing.main;

import com.bticketing.main.kafka.producer.PaymentEventProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@DirtiesContext //
public class PaymentKafkaTest {
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private PaymentEventProducer paymentEventProducer;

    private final CountDownLatch latch = new CountDownLatch(1);
    private String receivedEmailMessage;

    @BeforeEach
    void setup() {
        receivedEmailMessage = null; // 초기화
    }

    @Test
    void testKafkaFlow() throws InterruptedException {
        // Given
        String requestId = "testRequestId";
        int reservationId = 1;
        double amount = 100.0;

        String paymentMessage = String.format("requestId=%s,reservationId=%d,amount=%.2f", requestId, reservationId, amount);

        // When: payment-requested 이벤트 발행
        paymentEventProducer.sendPaymentRequestedEvent(paymentMessage);

        // Then: 메시지가 최종적으로 EmailNotificationService에서 처리되었는지 검증
        boolean messageReceived = latch.await(10, TimeUnit.SECONDS); // 10초 대기
        assertThat(messageReceived).isTrue();
        assertThat(receivedEmailMessage).contains("결제가 완료되었습니다. 예약 번호: 1, 금액: 100.0");
    }

    // KafkaListener를 통해 payment-completed 이벤트를 수신
    @KafkaListener(topics = "payment-completed", groupId = "payment-service-group")
    public void handlePaymentCompletedEvent(String message) {
        // message에서 이메일 메시지를 추출
        String[] parts = message.split(",");
        String reservationId = parts[1].split("=")[1];
        String amount = parts[2].split("=")[1];

        receivedEmailMessage = String.format("결제가 완료되었습니다. 예약 번호: %s, 금액: %s", reservationId, amount);
        latch.countDown(); // 메시지 수신 확인
    }
}
