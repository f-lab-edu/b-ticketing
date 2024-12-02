package com.bticketing.main.messaging;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;


@Component
public class RedisPubSubscriber {

    private final SimpMessagingTemplate messagingTemplate;

    public RedisPubSubscriber(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void onMessage(String message, String channel) {
        // 받은 메시지를 웹소켓 클라이언트에게 전송
        messagingTemplate.convertAndSend("/topic/seats", message);
    }
}
