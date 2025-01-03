package com.bticketing.main.kafka.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {
    private static final Logger logger = LoggerFactory.getLogger(PaymentEventListener.class);

    @KafkaListener(topics = "payment-completed", groupId = "payment-service-group")
    public void listenPaymentCompletedEvent(String message) {
        logger.info("Received payment completed event: {}", message);

        // 메시지를 처리 (알림 전송 등)
        processNotification(message);
    }

    private void processNotification(String message) {
        // 알림 처리 로직 (예: 이메일, SMS, 앱 푸시 등)
        logger.info("Processing notification for message: {}", message);
    }
}
