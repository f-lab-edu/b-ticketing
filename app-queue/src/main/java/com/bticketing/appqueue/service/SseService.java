package com.bticketing.appqueue.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private static final Logger logger = LoggerFactory.getLogger(SseService.class);
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // SSE Emitter 추가 메서드
    public SseEmitter addSseEmitter(String userToken) {
        SseEmitter emitter = new SseEmitter(60000L);
        emitters.put(userToken, emitter);

        // SSE 연결이 종료되면 자동으로 제거
        emitter.onCompletion(() -> emitters.remove(userToken));
        emitter.onTimeout(() -> emitters.remove(userToken));
        emitter.onError((e) -> emitters.remove(userToken));

        return emitter;
    }

    // SSE 이벤트 전송 메서드 수정
    public void sendEvent(String userToken, String eventName, String data) {
        SseEmitter emitter = emitters.get(userToken);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
                logger.info("SSE event '{}' sent to userToken: {}", eventName, userToken);
            } catch (Exception e) {
                emitters.remove(userToken);
                logger.error("Failed to send SSE event '{}' to userToken: {}", eventName, userToken, e);
            }
        } else {
            logger.warn("SSE emitter not found for userToken: {}", userToken);
        }
    }
    }

