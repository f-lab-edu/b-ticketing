package com.bticketing.appqueue;
import com.bticketing.appqueue.service.RedisQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public class SseEmitterSignalMockTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RedisQueueService redisQueueService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    public void setup() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testSseEmitterSignal_WhenCanProceedIsTrue() throws Exception {
        String userToken = "testUserToken";
        redisQueueService.addUserToQueue(userToken);

        // SSE 요청 전송 후 비동기 처리 기다림
        MvcResult mvcResult = mockMvc.perform(get("/queue/status")
                        .param("userToken", userToken)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        // canProceed 상태가 업데이트될 때까지 기다림
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            redisQueueService.updateCanProceedStatus();
            String canProceedStatus = (String) redisTemplate.opsForHash().get("user_status:" + userToken, "canProceed");
            assertThat("true".equals(canProceedStatus)).isTrue();
        });

        // 비동기 결과를 가져오도록 설정
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Proceed to /seats/sections")));
    }
}
