package com.bticketing.main.service;

import com.bticketing.main.messaging.WebSocketNotifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketTest {

    @LocalServerPort
    private int port;

    private StompSession stompSession;

    @Autowired
    private WebSocketNotifier webSocketNotifier;

    @BeforeEach
    void setup() throws Exception {
        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        // WebSocket 연결 설정
        stompSession = stompClient.connect(
                "ws://localhost:" + port + "/ws", // WebSocket 경로
                new StompSessionHandlerAdapter() {}
        ).get(5, TimeUnit.SECONDS); // 연결 대기 시간 증가
    }

    @Test
    void testWebSocketSendAndReceive() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        stompSession.subscribe("/topic/seats", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                assertEquals("{\"seatId\":1,\"status\":\"SELECTED\"}", payload);
                latch.countDown(); // 메시지 수신 후 카운트 감소
            }
        });

        // 메시지 전송
        stompSession.send("/app/send", "{\"seatId\":1,\"status\":\"SELECTED\"}");

        // 메시지 수신 대기
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertEquals(true, completed, "메시지를 수신하지 못했습니다.");
    }

    @Test
    void testWebSocketNotification() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        stompSession.subscribe("/topic/seats", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                assertEquals("{\"seatId\":1,\"status\":\"SELECTED\"}", payload);
                latch.countDown();
            }
        });

        // WebSocketNotifier를 통해 메시지 전송
        webSocketNotifier.notifySeatStatus(1, "SELECTED");

        // 메시지 수신 대기
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertEquals(true, completed, "메시지를 수신하지 못했습니다.");
    }

    @Test
    void testWebSocketInvalidMessage() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        stompSession.subscribe("/topic/errors", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                assertEquals("Invalid message format", payload);
                latch.countDown();
            }
        });

        // 잘못된 메시지 전송
        stompSession.send("/app/send", "INVALID_MESSAGE");

        // 메시지 수신 대기
        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertEquals(true, completed, "에러 메시지를 수신하지 못했습니다.");
    }
}
