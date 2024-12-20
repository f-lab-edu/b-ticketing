package com.bticketing.appqueue.controller;

import com.bticketing.appqueue.service.QueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QueueController {

    private final QueueService queueService;

    public QueueController(QueueService queueService) {
        this.queueService = queueService;
    }

    // 사용자 대기열 진입 API
    @GetMapping("/queue")
    public ResponseEntity<String> enterQueue(@RequestParam(required = false) String userToken) {
        try {
            String response = queueService.handleUserEntry(userToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error entering queue: " + e.getMessage());
        }
    }

    // Polling API: 사용자 리다이렉트 여부 확인
    @GetMapping("/queue/status")
    public ResponseEntity<String> checkQueueStatus(@RequestParam String userToken) {
        try {
            boolean readyToRedirect = queueService.isUserReadyToRedirect(userToken);
            return readyToRedirect ? ResponseEntity.ok("/seats/sections") : ResponseEntity.ok("inQueue");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error checking queue status: " + e.getMessage());
        }
    }
}
