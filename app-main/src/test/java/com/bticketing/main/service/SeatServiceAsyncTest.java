package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class SeatServiceAsyncTest {

    @Autowired
    private SeatService seatService;

    @Autowired
    private SeatReservationRepository seatReservationRepository;

    @Autowired
    private SeatRedisRepository redisRepository;

    @Autowired
    @Qualifier("threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        // DB 초기화
        redisRepository.clear();
        seatReservationRepository.deleteAll();
    }

    @Test
    void testThreadPoolConfiguration() {
        assertNotNull(taskExecutor, "ThreadPoolTaskExecutor should not be null");

        // 내부 ThreadPoolExecutor 확인
        ThreadPoolExecutor executor = taskExecutor.getThreadPoolExecutor();
        assertNotNull(executor, "ThreadPoolExecutor should not be null");

        // 스레드 풀 설정 확인
        assertEquals(10, executor.getCorePoolSize(), "Core pool size should be 10");
        assertEquals(50, executor.getMaximumPoolSize(), "Maximum pool size should be 50");
        assertEquals(100, taskExecutor.getQueueCapacity(), "Queue capacity should be 100");
        assertEquals("MyExecutor-", taskExecutor.getThreadNamePrefix(), "Thread name prefix should be 'MyExecutor-'");
    }

    @Test
    void testSelectSeatAsyncExecution() throws Exception {
        // Given
        int scheduleId = 1;
        int seatId = 10;

        // When
        CompletableFuture<SeatDto> future = seatService.selectSeat(scheduleId, seatId);

        // Then
        assertNotNull(future, "Future object should not be null");
        SeatDto seatDto = future.get(); // 비동기 작업 결과 가져오기
        assertNotNull(seatDto, "SeatDto should not be null");
        assertEquals("RESERVED", seatDto.getStatus(), "Seat status should be 'RESERVED'");
    }

    @Test
    void testSelectSeatAsyncThreadExecution() throws Exception {
        // Given
        int scheduleId = 1;
        int seatId = 11;

        // When
        CompletableFuture<SeatDto> future = seatService.selectSeat(scheduleId, seatId);

        // Then
        assertNotNull(future, "Future object should not be null");

        future.thenAccept(seatDto -> {
            assertNotNull(seatDto, "SeatDto should not be null");
            assertEquals("RESERVED", seatDto.getStatus(), "Seat status should be 'RESERVED'");

            String threadName = Thread.currentThread().getName();
            System.out.println("Async executed in thread: " + threadName);
            assertTrue(threadName.startsWith("MyExecutor-"),
                    "Expected execution in MyExecutor, but was: " + threadName);
        }).get(); // 비동기 작업이 완료될 때까지 대기
    }

    @Test
    void testAutoAssignSeatsAsyncThreadExecution() throws Exception {
        // Given
        int scheduleId = 2; // 테스트용 일정 ID
        int numSeats = 3;   // 요청 좌석 수

        // When
        CompletableFuture<List<SeatDto>> future = seatService.autoAssignSeats(scheduleId, numSeats);

        // Then
        assertNotNull(future, "Future object should not be null");

        future.thenAccept(seatDtos -> {
            assertNotNull(seatDtos, "SeatDtos list should not be null");
            assertEquals(numSeats, seatDtos.size(), "The number of assigned seats should match the requested number");

            String threadName = Thread.currentThread().getName();
            System.out.println("Async executed in thread: " + threadName);
            assertTrue(threadName.startsWith("MyExecutor-"),
                    "Expected execution in MyExecutor, but was: " + threadName);
        }).get(); // 비동기 작업이 완료될 때까지 대기
    }
}
