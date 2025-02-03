package com.bticketing.main.kafka.listener;

import com.bticketing.main.entity.Payment;
import com.bticketing.main.kafka.producer.PaymentEventProducer;
import com.bticketing.main.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger logger = LoggerFactory.getLogger(PaymentEventListener.class);
    private final PaymentService paymentService;
    private final PaymentEventProducer eventProducer; // Kafka Producer 추가

    public PaymentEventListener(PaymentService paymentService, PaymentEventProducer eventProducer) {
        this.paymentService = paymentService;
        this.eventProducer = eventProducer;
    }

    @KafkaListener(topics = "payment-requested", groupId = "payment-service-group")
    public void handlePaymentRequestedEvent(String message) {
        logger.info("Received message: {}", message);

        String[] parts = message.split(",");
        String requestId = parts[0].split("=")[1];
        int reservationId = Integer.parseInt(parts[1].split("=")[1]);
        double amount = Double.parseDouble(parts[2].split("=")[1]);

        try {
            Payment payment = paymentService.processPayment(requestId, reservationId, amount);
            logger.info("Payment processed successfully for requestId={}", requestId);

            //결과 완료 메시지 발행
            String completedMessage = String.format("requestId=%s,reservationId=%d,amount=%.2f,email=user@example.com",
                    requestId, reservationId, amount);
            eventProducer.sendPaymentCompletedEvent(completedMessage);
        } catch (Exception e) {
            logger.error("Error processing payment for requestId={}: {}", requestId, e.getMessage());
        }
    }
}
