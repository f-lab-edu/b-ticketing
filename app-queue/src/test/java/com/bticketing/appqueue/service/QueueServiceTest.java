package com.bticketing.appqueue.service;

import com.bticketing.appqueue.util.RedisUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;

import java.time.Duration;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class QueueServiceTest {

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private QueueService queueService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testHandleUserEntry_RedirectImmediately() {
        when(redisUtil.incrementValue("userQueue-size")).thenReturn(499L);
        when(redisUtil.acquireLock(anyString(), any(Duration.class))).thenReturn(true);

        String response = queueService.handleUserEntry("testToken");

        assertEquals("/seats/sections", response);
        verify(redisUtil, times(1)).setValueWithTTL(anyString(), eq(true), any());
    }

    @Test
    void testHandleUserEntry_AddedToQueue() {
        when(redisUtil.incrementValue("userQueue-size")).thenReturn(501L);
        when(redisUtil.acquireLock(anyString(), any(Duration.class))).thenReturn(true);
        when(redisUtil.getValue("currentGroup")).thenReturn(1);
        when(redisUtil.getListLength("group-1")).thenReturn(199L);

        // 수정된 부분: 반환 값이 없는 메서드 Mock 설정
        doNothing().when(redisUtil).rightPushToList("group-1", "testToken");

        String response = queueService.handleUserEntry("testToken");

        assertEquals("addedToQueue", response);
        verify(redisUtil, times(1)).rightPushToList("group-1", "testToken");
        verify(redisUtil, times(1)).setValueWithTTL(startsWith("userReady-"), eq(true), any());
    }

    @Test
    void testProcessQueueGroup() {
        // Mock 설정
        when(redisUtil.acquireLock(anyString(), any(Duration.class))).thenReturn(true);
        when(redisUtil.getValue("currentGroup")).thenReturn(1);
        when(redisUtil.getValue("group-1:size")).thenReturn(200L);
        when(redisUtil.incrementValue("group-1:size")).thenReturn(200L);

        // 사용자 토큰 리스트 반환 설정
        List<String> userTokens = List.of("token1", "token2", "token3");
        when(redisUtil.leftPopMultipleFromList("group-1", 200)).thenReturn(userTokens);

        // setMultipleValues() Mock 설정
        doAnswer(invocation -> {
            List<String> tokens = invocation.getArgument(0);
            tokens.forEach(token -> redisUtil.setValueWithTTL("userReady-" + token, true, Duration.ofMinutes(10)));
            return null;
        }).when(redisUtil).setMultipleValues(anyList(), eq(true), any());

        // 테스트 실행
        queueService.processQueueGroup();

        // 검증
        verify(redisUtil, times(1)).setMultipleValues(eq(userTokens), eq(true), any());
        verify(redisUtil, times(3)).setValueWithTTL(startsWith("userReady-"), eq(true), any());
        verify(redisUtil, times(1)).setValue("currentGroup", 2);
    }

    @Test
    void testIsUserReadyToRedirect() {
        when(redisUtil.getValue("userReady-testToken")).thenReturn(true);

        boolean isReady = queueService.isUserReadyToRedirect("testToken");

        assertTrue(isReady);
    }
}
