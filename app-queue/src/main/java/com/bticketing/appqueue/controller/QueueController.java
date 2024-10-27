package com.bticketing.appqueue.controller;


import com.bticketing.appqueue.dto.QueueDto;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queue")
public class QueueController {

    // 대기열 상태 조회 api
    @GetMapping("/status")
    public QueueDto getQueueStatus(@RequestParam int userId) {
        return new QueueDto(userId, 5, "대기 중");
    }

    // 대기열 진입 api
    @PostMapping("/enter")
    public QueueDto enterQueue(@RequestBody int userId) {
        return new QueueDto(userId, 6, "대기 중");
    }
}
