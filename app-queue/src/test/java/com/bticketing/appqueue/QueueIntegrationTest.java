package com.bticketing.appqueue;

import com.bticketing.appqueue.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QueueIntegrationTest {

    @Autowired
    private QueueService queueService;

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // 대기열 초기화
        for (int i = 0; i < 500; i++) {
            queueService.handleUserEntry("dummyUser" + i);
        }
    }

    @Test
    void testPollingAPI_UserInQueue() {
        // Given: 새로운 사용자 추가
        String userToken = "testToken";
        queueService.handleUserEntry(userToken);

        // When: Polling API 호출
        String statusUrl = UriComponentsBuilder.fromPath("/queue/status")
                .queryParam("userToken", userToken)
                .build()
                .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(statusUrl, String.class);

        // Then: 사용자 상태가 "inQueue"로 반환되는지 확인
        assertEquals("inQueue", response.getBody());
    }

    @Test
    void testPollingAPI_UserRedirected() {
        // Given: 리다이렉트 준비 완료
        String userToken = "redirectUser";

        // When: Polling API 호출
        String statusUrl = UriComponentsBuilder.fromPath("/queue/status")
                .queryParam("userToken", userToken)
                .build()
                .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(statusUrl, String.class);

        // Then: 사용자 상태가 "/seats/sections"로 반환되는지 확인
        assertEquals("/seats/sections", response.getBody());
    }
}
