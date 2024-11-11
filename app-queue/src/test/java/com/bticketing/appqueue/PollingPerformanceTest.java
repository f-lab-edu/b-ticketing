package com.bticketing.appqueue;

import com.bticketing.appqueue.service.RedisQueueListPollingService;
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
public class PollingPerformanceTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RedisQueueListPollingService pollingService;

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    /**
     * Polling 방식 성능 테스트
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    public void testPollingPerformance() throws Exception {
        // 5명의 사용자 요청을 동시에 처리
        for (int i = 1; i <= 5; i++) {
            String userToken = "pollingUser" + i;
            pollingService.addUserToQueue(userToken);
        }

        // 사용자 상태 조회 테스트
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= 5; i++) {
            String userToken = "pollingUser" + i;
            MvcResult result = mockMvc.perform(get("/polling/queue/status")
                            .param("userToken", userToken))
                    .andExpect(status().isOk())
                    .andReturn();

            String response = result.getResponse().getContentAsString();
            assertThat(response).isIn("Proceed to /seats/sections", "Waiting...");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        System.out.println("Polling 방식 테스트 소요 시간: " + duration + "ms");
    }
    //Polling 방식 테스트 소요 시간: 57ms
}
