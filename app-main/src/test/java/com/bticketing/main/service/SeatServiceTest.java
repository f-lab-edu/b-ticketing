package com.bticketing.main.service;

import com.bticketing.main.entity.Seat;
import com.bticketing.main.repository.RedisSeatRepository;
import com.bticketing.main.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeatServiceTest {

    private SeatRepository seatRepository;
    private RedisSeatRepository redisRepository;
    private SeatService seatService;

    @BeforeEach
    void setUp() {
        seatRepository = mock(SeatRepository.class);
        redisRepository = mock(RedisSeatRepository.class);
        seatService = new SeatService(seatRepository, redisRepository);
    }

    @Test
    void selectSeat_shouldSelectSeatWhenAvailable() {
        int seatId = 1;
        String seatKey = "seat:" + seatId;
        String lockKey = "seat:lock:" + seatId;

        // Mock Redis behavior
        when(redisRepository.executeWithLock(eq(lockKey), eq(5L), any())).thenAnswer(invocation -> {
            return ((Supplier<Boolean>) invocation.getArgument(2)).get();
        });
        when(redisRepository.getSeatStatus(seatKey)).thenReturn("AVAILABLE");

        // Mock DB behavior
        Seat mockSeat = new Seat(seatId, 1, 1, "AVAILABLE");
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(mockSeat));

        // Test the method
        boolean result = seatService.selectSeat(seatId);

        // Assertions
        assertTrue(result);
        verify(redisRepository).getSeatStatus(seatKey);
        verify(redisRepository).setSeatStatus(seatKey, "SELECTED", 300);
        verify(seatRepository).save(mockSeat);
    }

    @Test
    void selectSeat_shouldThrowExceptionWhenSeatIsNotAvailable() {
        int seatId = 1;
        String seatKey = "seat:" + seatId;
        String lockKey = "seat:lock:" + seatId;

        // Mock Redis behavior
        when(redisRepository.executeWithLock(eq(lockKey), eq(5L), any())).thenAnswer(invocation -> {
            return ((Supplier<Boolean>) invocation.getArgument(2)).get();
        });
        when(redisRepository.getSeatStatus(seatKey)).thenReturn("SELECTED");

        // Test the method
        RuntimeException exception = assertThrows(RuntimeException.class, () -> seatService.selectSeat(seatId));
        assertEquals("이미 선택된 좌석입니다.", exception.getMessage());
    }

    @Test
    void autoAssignSeats_shouldAssignSeatsWhenAvailable() {
        int scheduleId = 1;
        int sectionId = 1;
        int numSeats = 2;
        String lockKey = "autoAssign:lock:" + scheduleId + ":" + sectionId;

        // Mock Redis behavior
        when(redisRepository.executeWithLock(eq(lockKey), eq(5L), any())).thenAnswer(invocation -> {
            return ((Supplier<List<Seat>>) invocation.getArgument(2)).get();
        });

        // Mock DB behavior
        List<Seat> availableSeats = new ArrayList<>();
        availableSeats.add(new Seat(1, scheduleId, sectionId, "AVAILABLE"));
        availableSeats.add(new Seat(2, scheduleId, sectionId, "AVAILABLE"));
        when(seatRepository.findByScheduleIdAndSectionId(scheduleId, sectionId)).thenReturn(availableSeats);

        // Test the method
        List<Seat> assignedSeats = seatService.autoAssignSeats(scheduleId, sectionId, numSeats);

        // Assertions
        assertNotNull(assignedSeats);
        assertEquals(numSeats, assignedSeats.size());
        verify(redisRepository).setSeatStatus("seat:1", "SELECTED", 300);
        verify(redisRepository).setSeatStatus("seat:2", "SELECTED", 300);
    }

    @Test
    void autoAssignSeats_shouldThrowExceptionWhenNotEnoughSeats() {
        int scheduleId = 1;
        int sectionId = 1;
        int numSeats = 3;
        String lockKey = "autoAssign:lock:" + scheduleId + ":" + sectionId;

        // Mock Redis behavior
        when(redisRepository.executeWithLock(eq(lockKey), eq(5L), any())).thenAnswer(invocation -> {
            return ((Supplier<List<Seat>>) invocation.getArgument(2)).get();
        });

        // Mock DB behavior
        List<Seat> availableSeats = new ArrayList<>();
        availableSeats.add(new Seat(1, scheduleId, sectionId, "AVAILABLE"));
        when(seatRepository.findByScheduleIdAndSectionId(scheduleId, sectionId)).thenReturn(availableSeats);

        // Test the method
        RuntimeException exception = assertThrows(RuntimeException.class, () -> seatService.autoAssignSeats(scheduleId, sectionId, numSeats));
        assertEquals("사용 가능한 좌석이 부족합니다.", exception.getMessage());
    }
}
