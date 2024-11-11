package com.bticketing.appqueue.controller;

import com.bticketing.appqueue.service.RedisQueueListService;
import com.bticketing.appqueue.service.SseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/sse/queue")
public class QueueSseController {

    private final RedisQueueListService queueService;
    private final SseService sseService;

    @Autowired
    public QueueSseController(RedisQueueListService queueService, SseService sseService) {
        this.queueService = queueService;
        this.sseService = sseService;
    }

    @GetMapping("/seats")
    public ResponseEntity<String> getSeatList(@RequestParam String userToken) {
        queueService.addUserToQueue(userToken);
        return ResponseEntity.ok(userToken);
    }

    @GetMapping(value = "/status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queueStatus(@RequestParam String userToken) {
        return sseService.addSseEmitter(userToken);
    }
}
