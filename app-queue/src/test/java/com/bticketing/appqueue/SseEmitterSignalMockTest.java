package com.bticketing.appqueue;

import com.bticketing.appqueue.service.RedisQueueService;
import com.bticketing.appqueue.service.SseService;
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

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private SseService sseService;  // SseService 주입

    @BeforeEach
    public void setup() {
        redisTemplate.getConnectionFactory().getConnection().flushAll();
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testSseEmitterSignal_WhenCanProceedIsTrue() throws Exception {
        String userToken = "testUserToken";
        redisQueueService.addUserToQueue(userToken);

        MvcResult mvcResult = mockMvc.perform(get("/queue/status")
                        .param("userToken", userToken)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            redisQueueService.updateCanProceedStatus();
            String canProceedStatus = (String) redisTemplate.opsForHash().get("user_status:" + userToken, "canProceed");
            assertThat("true".equals(canProceedStatus)).isTrue();
        });

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Proceed to /seats/sections")));
    }
}
