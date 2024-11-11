package com.bticketing.appqueue.controller;

import com.bticketing.appqueue.service.RedisQueueListPollingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/polling/queue")
public class QueuePollingController {

    private final RedisQueueListPollingService queueService;

    @Autowired
    public QueuePollingController(RedisQueueListPollingService queueService) {
        this.queueService = queueService;
    }

    // 사용자 대기열 추가 요청
    @GetMapping("/seats")
    public ResponseEntity<String> getSeatList(@RequestParam String userToken) {
        queueService.addUserToQueue(userToken);
        return ResponseEntity.ok(userToken);
    }

    // Polling 요청 처리: 사용자 상태 조회
    @GetMapping("/status")
    public ResponseEntity<String> getPollingStatus(@RequestParam String userToken) {
        String status = queueService.getUserStatus(userToken);
        return ResponseEntity.ok(status);
    }
}
