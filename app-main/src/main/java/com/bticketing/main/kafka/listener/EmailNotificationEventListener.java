package com.bticketing.main.kafka.listener;

import com.bticketing.main.service.EmailNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationEventListener {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationEventListener.class);
    private final EmailNotificationService emailNotificationService;

    public EmailNotificationEventListener(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @KafkaListener(topics = "payment-completed", groupId = "notification-service-group")
    public void handlePaymentCompletedEvent(String message) {

        String[] parts = message.split(",");
        String email = parts[3].split("=")[1];
        String reservationId = parts[1].split("=")[1];
        String amount = parts[2].split("=")[1];

        String emailMessage = String.format("결제가 완료되었습니다. 예약 번호: %s, 금액: %s", reservationId, amount);
        emailNotificationService.sendNotification(email, emailMessage);
    }
}
