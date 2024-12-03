package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeatServiceTest {

    @Mock
    private SeatRedisRepository redisRepository;

    @Mock
    private SeatReservationRepository seatReservationRepository;

    @InjectMocks
    private SeatService seatService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testSelectSeat_Success() {
        int seatId = 101;
        int scheduleId = 1;
        String lockKey = "seat:lock:" + scheduleId + ":" + seatId;
        String seatKey = "seat:" + scheduleId + ":" + seatId;

        // RedisRepository의 동작 모킹
        when(redisRepository.getSeatStatus(seatKey)).thenReturn(null); // 좌석이 비어있는 상태
        when(redisRepository.executeWithLock(eq(lockKey), eq(5L), any()))
                .thenAnswer(invocation -> {
                    Supplier<SeatDto> action = invocation.getArgument(2); // Supplier로 캐스팅
                    return action.get(); // Supplier 실행
                });

        // SeatService의 selectSeat 호출
        SeatDto result = seatService.selectSeat(scheduleId, seatId);

        // 결과 검증
        assertEquals(seatId, result.getSeatId());
        assertEquals("RESERVED", result.getStatus());

        // Redis 호출 검증
        verify(redisRepository, times(2)).getSeatStatus(seatKey);
        verify(redisRepository).setSeatStatus(eq(seatKey), eq("RESERVED"), eq(300L));
    }

    @Test
    void testSelectSeat_AlreadyReserved() {
        int scheduleId = 101;
        int seatId = 1;
        String seatKey = "seat:" + scheduleId + ":" + seatId;

        // Mock Redis getSeatStatus
        when(redisRepository.getSeatStatus(seatKey)).thenReturn("RESERVED");

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> seatService.selectSeat(scheduleId, seatId));
        assertEquals("이미 예약된 좌석입니다.", exception.getMessage());
        verify(redisRepository, never()).setSeatStatus(anyString(), anyString(), anyLong());
    }

    @Test
    void testAutoAssignSeats_Success() {
        int scheduleId = 100;
        int numSeats = 2;

        List<SeatReservation> seatReservations = Arrays.asList(
                new SeatReservation(1, new Seat(1, "A", 1), 100, "PENDING"),
                new SeatReservation(2, new Seat(2, "A", 2), 100, "PENDING"),
                new SeatReservation(3, new Seat(3, "A", 3), 100, "COMPLETED")
        );

        Map<String, String> reservedSeats = Map.of(
                "seat:100:3", "RESERVED"
        );

        // Mock DB repository
        when(seatReservationRepository.findByScheduleId(scheduleId)).thenReturn(seatReservations);

        // Mock Redis repository
        when(redisRepository.getAllReservedSeats(scheduleId)).thenReturn(reservedSeats);

        // Act
        List<SeatDto> result = seatService.autoAssignSeats(scheduleId, numSeats);

        // Assert
        assertEquals(2, result.size());
        assertEquals("RESERVED", result.get(0).getStatus());
        assertEquals("RESERVED", result.get(1).getStatus());
        verify(redisRepository, times(2)).setSeatStatus(anyString(), eq("RESERVED"), eq(300L));
    }

    @Test
    void testAutoAssignSeats_Failure() {
        int scheduleId = 100;
        int numSeats = 4;

        List<SeatReservation> seatReservations = Arrays.asList(
                new SeatReservation(1, new Seat(1, "A", 1), 100, "PENDING"),
                new SeatReservation(2, new Seat(2, "A", 2), 100, "PENDING")
        );

        Map<String, String> reservedSeats = Map.of(
                "seat:100:1", "RESERVED",
                "seat:100:2", "RESERVED"
        );

        // Mock DB repository
        when(seatReservationRepository.findByScheduleId(scheduleId)).thenReturn(seatReservations);

        // Mock Redis repository
        when(redisRepository.getAllReservedSeats(scheduleId)).thenReturn(reservedSeats);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> seatService.autoAssignSeats(scheduleId, numSeats));
        assertEquals("요청한 좌석 수를 자동 배정할 수 없습니다.", exception.getMessage());
        verify(redisRepository, never()).setSeatStatus(anyString(), anyString(), anyLong());
    }

    @Test
    void testGetSeatsStatus() {
        int scheduleId = 100;

        List<SeatReservation> completedReservations = Arrays.asList(
                new SeatReservation(1, new Seat(1, "A", 1), 100, "COMPLETED"),
                new SeatReservation(2, new Seat(2, "A", 2), 100, "COMPLETED")
        );

        Map<String, String> reservedSeats = Map.of(
                "seat:100:3", "RESERVED"
        );

        // Mock DB and Redis repository
        when(seatReservationRepository.findByScheduleIdAndStatus(scheduleId, "COMPLETED"))
                .thenReturn(completedReservations);
        when(redisRepository.getAllReservedSeats(scheduleId)).thenReturn(reservedSeats);

        // Act
        List<SeatDto> result = seatService.getSeatsStatus(scheduleId);

        // Assert
        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(seat -> seat.getSeatId() == 1 && "COMPLETED".equals(seat.getStatus())));
        assertTrue(result.stream().anyMatch(seat -> seat.getSeatId() == 2 && "COMPLETED".equals(seat.getStatus())));
        assertTrue(result.stream().anyMatch(seat -> seat.getSeatId() == 3 && "RESERVED".equals(seat.getStatus())));
    }
}
