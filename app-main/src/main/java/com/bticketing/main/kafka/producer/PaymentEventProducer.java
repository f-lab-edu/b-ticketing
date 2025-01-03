package com.bticketing.main.kafka.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {
    private static final Logger logger = LoggerFactory.getLogger(PaymentEventProducer.class);
    private static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";

    private final KafkaTemplate<String, String> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPaymentCompletedEvent(String message) {
        logger.info("Producing payment completed event to topic: {}", PAYMENT_COMPLETED_TOPIC);
        kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, message);
    }
}
