package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.exception.SeatAlreadyReservedException;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeatServiceTest {

    @Mock
    private SeatRedisRepository redisRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private SeatReservationRepository seatReservationRepository;

    @InjectMocks
    private SeatService seatService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // 각 테스트가 독립적으로 Mock 상태를 갖도록 초기화
    }

    @AfterEach
    void tearDown() {
        // Mock 상태를 초기화하여 테스트 간 간섭 방지
        clearInvocations(redisRepository, seatRepository, seatReservationRepository);
        reset(redisRepository, seatRepository, seatReservationRepository);
    }

    @Test
    void testSelectSeat_RedisAndDbEmpty() {
        int scheduleId = 1;
        int seatId = 10;
        String lockKey = "seat:lock:" + scheduleId + ":" + seatId;
        String seatKey = "seat:" + scheduleId + ":" + seatId;

        // Mock 설정
        when(redisRepository.getSeatStatus(eq(seatKey)))
                .thenReturn(null) // Redis 초기 상태 없음
                .thenReturn("AVAILABLE"); // 동기화 후 상태 "AVAILABLE"로 설정

        when(seatReservationRepository.findBySeatAndSchedule(eq(seatId), eq(scheduleId)))
                .thenReturn(Optional.empty()); // DB에도 예약 정보 없음

        Seat mockSeat = new Seat(seatId, "A", 1);
        when(seatRepository.findById(eq(seatId))).thenReturn(Optional.of(mockSeat)); // Seat 조회 Mock

        when(seatReservationRepository.save(any(SeatReservation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0)); // 저장된 객체 반환

        // Redis 상태 업데이트 Mock
        doNothing().when(redisRepository).setSeatStatus(anyString(), anyString(), anyLong());

        // executeWithLock Mock 설정
        when(redisRepository.executeWithLock(eq(lockKey), eq(300L), any()))
                .thenAnswer(invocation -> {
                    Supplier<SeatDto> action = invocation.getArgument(2); // Supplier 가져오기
                    return action.get(); // Supplier 실행
                });

        // 테스트 실행
        SeatDto result = seatService.selectSeat(scheduleId, seatId);

        // Mock 호출 검증
        verify(redisRepository, times(1)).getSeatStatus(eq(seatKey)); // 초기 Redis 상태 조회
        verify(seatReservationRepository, times(1)).findBySeatAndSchedule(eq(seatId), eq(scheduleId)); // DB 조회
        verify(seatRepository, times(1)).findById(eq(seatId)); // Seat 조회
        verify(redisRepository, times(1)).setSeatStatus(eq(seatKey), eq("AVAILABLE"), anyLong()); // Redis 동기화
        verify(redisRepository, times(1)).executeWithLock(eq(lockKey), eq(300L), any()); // 락 획득 후 실행

        // 결과 검증
        assertNotNull(result, "Result should not be null");
        assertEquals("RESERVED", result.getStatus());
        assertEquals(seatId, result.getSeatId());
    }

    @Test
    void testSelectSeat_RedisReserved() {
        int scheduleId = 1;
        int seatId = 10;

        // Redis에서 이미 예약된 상태 설정
        when(redisRepository.getSeatStatus("seat:1:10")).thenReturn("RESERVED");

        // 예약된 상태에서 예외 발생 확인
        assertThrows(SeatAlreadyReservedException.class, () -> seatService.selectSeat(scheduleId, seatId));
    }

    @Test
    void testSelectSeat_DbReserved() {
        int scheduleId = 1;
        int seatId = 10;

        // Redis는 비어있지만 DB에 예약 정보가 있는 상태 설정
        when(redisRepository.getSeatStatus("seat:1:10")).thenReturn(null);
        when(seatReservationRepository.findBySeatAndSchedule(seatId, scheduleId))
                .thenReturn(Optional.of(new SeatReservation(0, new Seat(seatId, "A", 1), scheduleId, "RESERVED")));

        // Redis 업데이트 Mock 설정
        doNothing().when(redisRepository).setSeatStatus(anyString(), anyString(), anyLong());

        // 예약된 상태에서 예외 발생 확인
        assertThrows(SeatAlreadyReservedException.class, () -> seatService.selectSeat(scheduleId, seatId));
    }
}
