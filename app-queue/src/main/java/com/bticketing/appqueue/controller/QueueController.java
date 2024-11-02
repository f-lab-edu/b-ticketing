package com.bticketing.appqueue.controller;

import com.bticketing.appqueue.service.RedisQueueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public String getSeatList(@RequestParam(required = false) String userToken) {
        // 로그인된 사용자의 경우
        if (userToken != null) {
            // VIP 여부 확인
            if (queueService.isUserVIP(userToken)) {
                return "VIP access granted to: " + userToken + ". Redirecting to seat selection.";
            } else {
                queueService.addUserToQueue(userToken);
                return "User added to queue: " + userToken + ". Please wait for your turn.";
            }
        }
        // 비회원의 경우 임시 토큰 발급 후 대기열 추가
        else {
            String guestToken = queueService.generateGuestToken();
            queueService.addUserToQueue(guestToken);
            return "Guest access granted with temporary token: " + guestToken + ". Please wait for your turn.";
        }
    }
}
