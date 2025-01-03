package com.bticketing.main.service;

import com.bticketing.main.entity.Payment;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.repository.payment.PaymentRepository;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SeatReservationRepository seatReservationRepository;

    @Mock
    private SeatRedisRepository redisRepository;

    @InjectMocks
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this); // Mock 객체 초기화
    }

    @Test
    void processPayment_Success() {
        // Given
        int reservationId = 1;
        double amount = 100.0;

        Payment pendingPayment = new Payment();
        pendingPayment.setReservationId(reservationId);
        pendingPayment.setPaymentStatus("PENDING");
        pendingPayment.setAmount(amount);
        pendingPayment.setPaymentDate(LocalDateTime.now());

        Payment completedPayment = new Payment();
        completedPayment.setReservationId(reservationId);
        completedPayment.setPaymentStatus("COMPLETED");
        completedPayment.setAmount(amount);
        completedPayment.setPaymentDate(LocalDateTime.now());

        Seat seat = new Seat();
        seat.setSeatId(1);
        SeatReservation reservation = new SeatReservation();
        reservation.setScheduleId(1);
        reservation.setSeat(seat);
        reservation.setStatus("PENDING");

        when(paymentRepository.save(any(Payment.class)))
                .thenReturn(pendingPayment)
                .thenReturn(completedPayment);
        when(seatReservationRepository.findById(reservationId))
                .thenReturn(Optional.of(reservation));

        // When
        Payment result = paymentService.processPayment(reservationId, amount);

        // Then
        assertNotNull(result);
        assertEquals("COMPLETED", result.getPaymentStatus());
        verify(paymentRepository, times(2)).save(any(Payment.class)); // 두 번 호출: PENDING → COMPLETED
        verify(seatReservationRepository).findById(reservationId);
        verify(seatReservationRepository).save(reservation);
        verify(redisRepository).setSeatStatus("seat:1:1", "COMPLETE");
    }

    @Test
    void processPayment_Failure() {
        // Given
        int reservationId = 1;
        double amount = 100.0;

        Payment pendingPayment = new Payment();
        pendingPayment.setReservationId(reservationId);
        pendingPayment.setPaymentStatus("PENDING");
        pendingPayment.setAmount(amount);
        pendingPayment.setPaymentDate(LocalDateTime.now());

        when(paymentRepository.save(any(Payment.class))).thenReturn(pendingPayment);
        when(seatReservationRepository.findById(reservationId))
                .thenThrow(new RuntimeException("결제 처리 중 오류가 발생했습니다."));

        // When / Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            paymentService.processPayment(reservationId, amount);
        });

        // 메시지 검증
        assertEquals("결제 처리 중 오류가 발생했습니다.", exception.getMessage());

        // 호출 횟수 검증
        verify(paymentRepository, times(3)).save(any(Payment.class)); // PENDING → FAILED
        verify(seatReservationRepository, times(2)).findById(reservationId); // processPayment와 revertSeatStatusToAvailable
        verify(redisRepository, never()).setSeatStatus(anyString(), anyString()); // Redis 호출 없음

        // 결과 상태 검증
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(paymentCaptor.capture());

        List<Payment> savedPayments = paymentCaptor.getAllValues();
        assertEquals("FAILED", savedPayments.get(savedPayments.size() - 1).getPaymentStatus()); // 최종 상태
    }

    @Test
    void cancelPayment_Success() {
        // Given
        int reservationId = 1;

        Seat seat = new Seat();
        seat.setSeatId(1);
        SeatReservation reservation = new SeatReservation();
        reservation.setScheduleId(1);
        reservation.setSeat(seat);
        reservation.setStatus("RESERVED");

        Payment payment = new Payment();
        payment.setReservationId(reservationId);
        payment.setPaymentStatus("COMPLETED");

        when(seatReservationRepository.findById(reservationId))
                .thenReturn(Optional.of(reservation));
        when(paymentRepository.findById(reservationId))
                .thenReturn(Optional.of(payment));

        // When
        paymentService.cancelPayment(reservationId);

        // Then
        verify(seatReservationRepository).findById(reservationId);
        verify(seatReservationRepository).save(reservation);
        assertEquals("AVAILABLE", reservation.getStatus());
        verify(redisRepository).setSeatStatus("seat:1:1", "AVAILABLE");

        verify(paymentRepository).findById(reservationId);
        verify(paymentRepository).save(payment);
        assertEquals("CANCELED", payment.getPaymentStatus());
    }
}
