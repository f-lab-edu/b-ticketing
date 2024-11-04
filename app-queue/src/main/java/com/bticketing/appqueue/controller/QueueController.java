package com.bticketing.appqueue.controller;

import com.bticketing.appqueue.service.RedisQueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/queue")
public class QueueController {

    private final RedisQueueService queueService;

    @Autowired
    public QueueController(RedisQueueService queueService) {
        this.queueService = queueService;
    }

    // 좌석 목록 요청 (로그인/비회원 처리)
    @GetMapping("/seats")
    public ResponseEntity<String> getSeatList(@RequestParam(required = false) String userToken) {
        // 로그인된 사용자의 경우
        if (userToken != null) {
            // VIP 여부 확인
            if (queueService.isUserVIP(userToken)) {
                return ResponseEntity.ok("/seats/sections");
            } else {
                queueService.addUserToQueue(userToken);
                return ResponseEntity.ok( userToken);
            }
        }
        // 비회원의 경우 임시 토큰 발급 후 대기열 추가
        else {
            String guestToken = queueService.generateGuestToken();
            queueService.addUserToQueue(guestToken);
            return ResponseEntity.ok(guestToken );
        }
    }

    // SSE 연결 설정
    @GetMapping(value = "/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queueStatus(@RequestParam String userToken) {
        return queueService.addSseEmitter(userToken);
    }
}
