package com.bticketing.main.messaging;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class WebSocketNotifier {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketNotifier(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifySeatStatus(int seatId, String status) {
        String message = String.format("{\"seatId\":%d,\"status\":\"%s\"}", seatId, status);
        messagingTemplate.convertAndSend("/topic/seats", message);
    }
}
