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
        int scheduleId = 1;
        int seatId = 101;
        String lockKey = "seat:lock:" + scheduleId + ":" + seatId;
        String seatKey = "seat:" + scheduleId + ":" + seatId;

        // Mock 설정: 첫 번째 호출에서는 비어있는 좌석
        when(redisRepository.getSeatStatus(seatKey))
                .thenReturn(null) // 첫 번째 호출: 빈 좌석
                .thenReturn(null); // 두 번째 호출: 락 이후에도 빈 좌석

        // Mock executeWithLock: 락 동작 시 Supplier 실행
        when(redisRepository.executeWithLock(eq(lockKey), eq(300L), any()))
                .thenAnswer(invocation -> {
                    Supplier<SeatDto> action = invocation.getArgument(2); // Supplier 가져오기
                    return action.get(); // Supplier 실행
                });

        // 서비스 호출
        SeatDto result = seatService.selectSeat(scheduleId, seatId);

        // 검증
        assertNotNull(result, "Result should not be null");
        assertEquals(seatId, result.getSeatId());
        assertEquals("RESERVED", result.getStatus());

        // Mock 호출 검증
        verify(redisRepository, times(2)).getSeatStatus(seatKey);
        verify(redisRepository).setSeatStatus(seatKey, "RESERVED", 300L);
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
        int scheduleId = 1;
        int numSeats = 2;

        // 전체 좌석 데이터
        List<Seat> allSeats = Arrays.asList(
                new Seat(101, "A", 1),
                new Seat(102, "A", 2),
                new Seat(103, "A", 3)
        );

        // Mock DB behavior: 좌석 예약 정보 (예약된 좌석은 없음)
        List<SeatReservation> seatReservations = allSeats.stream()
                .map(seat -> new SeatReservation(seat.getSeatId(), seat, scheduleId, "AVAILABLE"))
                .toList();
        when(seatReservationRepository.findByScheduleId(scheduleId)).thenReturn(seatReservations);

        // Mock Redis behavior: 예약된 좌석 없음
        when(redisRepository.getAllReservedSeats(scheduleId)).thenReturn(Collections.emptyMap());

        // Act
        List<SeatDto> result = seatService.autoAssignSeats(scheduleId, numSeats);

        // Assert
        assertNotNull(result);
        assertEquals(numSeats, result.size());
        assertEquals(101, result.get(0).getSeatId());
        assertEquals("RESERVED", result.get(0).getStatus());
        assertEquals(102, result.get(1).getSeatId());
        assertEquals("RESERVED", result.get(1).getStatus());

        // Redis 업데이트 검증
        verify(redisRepository).setSeatStatus("seat:1:101", "RESERVED", 300L);
        verify(redisRepository).setSeatStatus("seat:1:102", "RESERVED", 300L);
    }

    @Test
    void testAutoAssignSeats_Failure() {
        int scheduleId = 1;
        int numSeats = 3;

        // Mock DB behavior: No available seats
        when(seatReservationRepository.findByScheduleId(scheduleId)).thenReturn(Collections.emptyList());

        // Mock Redis behavior: No available seats
        when(redisRepository.getAllReservedSeats(scheduleId)).thenReturn(Collections.emptyMap());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> seatService.autoAssignSeats(scheduleId, numSeats));
        assertEquals("요청한 좌석 수를 자동 배정할 수 없습니다.", exception.getMessage());

        verify(redisRepository, never()).setSeatStatus(anyString(), anyString(), anyLong());
    }


    @Test
    void testGetSeatsStatus() {
        int scheduleId = 1;

        // Mock Redis data
        Map<String, String> redisData = Map.of(
                "seat:1:101", "RESERVED",
                "seat:1:102", "RESERVED"
        );
        when(redisRepository.getAllReservedSeats(scheduleId)).thenReturn(redisData);

        // Mock DB data
        List<SeatReservation> completedSeats = Arrays.asList(
                new SeatReservation(1, new Seat(103, "A", 3), scheduleId, "COMPLETED"),
                new SeatReservation(2, new Seat(104, "A", 4), scheduleId, "COMPLETED")
        );
        when(seatReservationRepository.findByScheduleIdAndStatus(scheduleId, "COMPLETED"))
                .thenReturn(completedSeats);

        // Act
        List<SeatDto> result = seatService.getSeatsStatus(scheduleId);

        // Assert
        assertNotNull(result, "Result should not be null");
        assertEquals(4, result.size(), "Result should contain 4 seats");

        assertTrue(result.stream().anyMatch(seat -> seat.getSeatId() == 101 && "RESERVED".equals(seat.getStatus())),
                "Seat 101 should be RESERVED");
        assertTrue(result.stream().anyMatch(seat -> seat.getSeatId() == 102 && "RESERVED".equals(seat.getStatus())),
                "Seat 102 should be RESERVED");
        assertTrue(result.stream().anyMatch(seat -> seat.getSeatId() == 103 && "COMPLETED".equals(seat.getStatus())),
                "Seat 103 should be COMPLETED");
        assertTrue(result.stream().anyMatch(seat -> seat.getSeatId() == 104 && "COMPLETED".equals(seat.getStatus())),
                "Seat 104 should be COMPLETED");

        // Verify Redis and DB methods were called
        verify(redisRepository).getAllReservedSeats(scheduleId);
        verify(seatReservationRepository).findByScheduleIdAndStatus(scheduleId, "COMPLETED");
    }
}
