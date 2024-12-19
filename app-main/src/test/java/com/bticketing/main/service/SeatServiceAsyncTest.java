package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EnableAsync
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
        assertNotNull(taskExecutor);

        // 내부 ThreadPoolExecutor 확인
        ThreadPoolExecutor executor = taskExecutor.getThreadPoolExecutor();
        assertNotNull(executor);

        // 스레드 풀 설정 확인
        assertEquals(10, executor.getCorePoolSize());
        assertEquals(50, executor.getMaximumPoolSize());
        assertEquals(100, taskExecutor.getQueueCapacity());
        assertEquals("MyExecutor-", taskExecutor.getThreadNamePrefix());
    }


    @Test
    void testSelectSeatAsyncExecution() throws Exception {
        // Given
        int scheduleId = 1;
        int seatId = 10;

        // When
        CompletableFuture<SeatDto> future = seatService.selectSeat(scheduleId, seatId);

        // Then
        assertNotNull(future); // future가 null인지 확인
        SeatDto seatDto = future.get(); // 비동기 작업 결과 가져오기
        assertNotNull(seatDto); // 반환된 객체가 null인지 확인
        assertEquals("RESERVED", seatDto.getStatus()); // 상태 확인
    }

    @Test
    void testSelectSeatAsyncThreadExecution() throws Exception {
        // Given
        int scheduleId = 1;
        int seatId = 11;

        // When
        CompletableFuture<SeatDto> future = seatService.selectSeat(scheduleId, seatId);

        // Then
        assertNotNull(future); // future가 null인지 확인

        future.thenAccept(seatDto -> {
            assertNotNull(seatDto); // 반환된 객체가 null인지 확인
            assertEquals("RESERVED", seatDto.getStatus()); // 상태 확인

            String threadName = Thread.currentThread().getName();
            System.out.println("Async executed in thread: " + threadName);
            assertTrue(threadName.startsWith("MyExecutor-")); // 스레드 이름 확인
        }).get(); // 결과를 가져오며 비동기 작업 대기
    }

    @Test
    void testAutoAssignSeatsAsyncThreadExecution() throws Exception {
        // Given
        int scheduleId = 2; // 테스트용 일정 ID
        int numSeats = 3;   // 요청 좌석 수

        // When
        CompletableFuture<List<SeatDto>> future = seatService.autoAssignSeats(scheduleId, numSeats);

        // Then
        assertNotNull(future, "The future object should not be null");

        // 비동기 작업 완료 대기 및 스레드 이름 확인
        future.thenAcceptAsync(seatDtos -> {
            assertNotNull(seatDtos, "The seatDtos list should not be null");
            String threadName = Thread.currentThread().getName();
            System.out.println("Async executed in thread: " + threadName);
            assertTrue(threadName.startsWith("MyExecutor-"),
                    "Expected execution in MyExecutor, but was: " + threadName);
        }, taskExecutor).get();// get()을 호출하여 비동기 작업이 완료될 때까지 기다림
    }

}
