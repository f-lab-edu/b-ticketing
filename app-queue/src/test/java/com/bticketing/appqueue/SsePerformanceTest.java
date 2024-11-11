package com.bticketing.appqueue;

import com.bticketing.appqueue.service.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class SsePerformanceTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SseService sseService;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /**
     * SSE 방식 성능 테스트
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testSsePerformance() throws Exception {
        // 5명의 사용자 요청을 동시에 처리
        for (int i = 1; i <= 5; i++) {
            String userToken = "sseUser" + i;
            sseService.addSseEmitter(userToken); // SSE 연결을 추가
        }

        // 사용자 상태 조회 테스트
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= 5; i++) {
            String userToken = "sseUser" + i;
            MvcResult result = mockMvc.perform(get("/sse/queue/status")
                            .param("userToken", userToken)
                            .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                    .andExpect(status().isOk())
                    .andReturn();

            String response = result.getResponse().getContentAsString();
            assertThat(response).contains("data: Proceed to /seats/sections", "data: Waiting...");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        System.out.println("SSE 방식 테스트 소요 시간: " + duration + "ms");
    }
}
