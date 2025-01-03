package com.bticketing.main.kafka.listener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaListenerService {

    @KafkaListener(topics = "payment-notifications", groupId = "payment-notification-group")
    public void listen(String message) {
        System.out.println("Received message: " + message);
    }
}
