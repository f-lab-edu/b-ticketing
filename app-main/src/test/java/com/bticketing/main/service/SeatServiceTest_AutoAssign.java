package com.bticketing.main.service;

import com.bticketing.main.dto.SeatDto;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.exception.SeatsNotAvailableException;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SeatServiceTest_AutoAssign {

//    @Mock
//    private SeatRedisRepository redisRepository;
//
//    @Mock
//    private SeatRepository seatRepository;
//
//    @Mock
//    private SeatReservationRepository seatReservationRepository;
//
//    @InjectMocks
//    private SeatService seatService;
//
//    public SeatServiceTest_AutoAssign() {
//        MockitoAnnotations.openMocks(this);
//    }
//
//    @Test
//    void testAutoAssignSeats_RedisHasAvailableSeats() {
//        int scheduleId = 1;
//        int numSeats = 3;
//        List<Integer> redisAvailableSeats = Arrays.asList(1, 2, 3, 4);
//
//        when(redisRepository.getAvailableSeatIds(scheduleId)).thenReturn(redisAvailableSeats);
//
//        List<SeatDto> assignedSeats = seatService.autoAssignSeats(scheduleId, numSeats);
//
//        verify(redisRepository).setSeatStatus("seat:1:1", "RESERVED", 300);
//        verify(redisRepository).setSeatStatus("seat:1:2", "RESERVED", 300);
//        verify(redisRepository).setSeatStatus("seat:1:3", "RESERVED", 300);
//
//        assertEquals(numSeats, assignedSeats.size());
//        assertEquals("RESERVED", assignedSeats.get(0).getStatus());
//    }
//
//    @Test
//    void testAutoAssignSeats_NoRedisAvailableSeats_DbHasAvailableSeats() {
//        int scheduleId = 1;
//        int numSeats = 3;
//        List<SeatReservation> dbAvailableSeats = Arrays.asList(
//                new SeatReservation(0, new Seat(10, "A", 1), scheduleId, "AVAILABLE"),
//                new SeatReservation(0, new Seat(11, "A", 2), scheduleId, "AVAILABLE"),
//                new SeatReservation(0, new Seat(12, "A", 3), scheduleId, "AVAILABLE")
//        );
//
//        when(redisRepository.getAvailableSeatIds(scheduleId)).thenReturn(List.of());
//        when(seatReservationRepository.findAvailableSeats(scheduleId)).thenReturn(dbAvailableSeats);
//
//        List<SeatDto> assignedSeats = seatService.autoAssignSeats(scheduleId, numSeats);
//
//        verify(redisRepository).setSeatStatus("seat:1:10", "RESERVED", 300);
//        verify(redisRepository).setSeatStatus("seat:1:11", "RESERVED", 300);
//        verify(redisRepository).setSeatStatus("seat:1:12", "RESERVED", 300);
//
//        assertEquals(numSeats, assignedSeats.size());
//    }
//
//    @Test
//    void testAutoAssignSeats_NoRedisOrDbSeats() {
//        int scheduleId = 1;
//        int numSeats = 3;
//
//        when(redisRepository.getAvailableSeatIds(scheduleId)).thenReturn(List.of());
//        when(seatReservationRepository.findAvailableSeats(scheduleId)).thenReturn(List.of());
//
//        assertThrows(SeatsNotAvailableException.class, () -> seatService.autoAssignSeats(scheduleId, numSeats));
//    }
}
