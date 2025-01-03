package com.bticketing.main.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EmailNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailNotificationService.class);

    public void sendNotification(String email, String message) {
        logger.info("사용자님의 메일({})로 결제 완료 메일이 발송되었습니다.", email);
        System.out.println("사용자님의 메일(" + email + ")로 결제 완료 메일이 발송되었습니다.");
        System.out.println("내용: " + message);
    }
}
