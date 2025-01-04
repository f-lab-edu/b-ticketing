package com.bticketing.main.service;

import com.bticketing.main.entity.Payment;
import com.bticketing.main.entity.Seat;
import com.bticketing.main.entity.SeatReservation;
import com.bticketing.main.kafka.producer.PaymentEventProducer;
import com.bticketing.main.repository.payment.PaymentRepository;
import com.bticketing.main.repository.redis.PaymentRedisRepository;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SeatReservationRepository seatReservationRepository;

    @Mock
    private SeatRedisRepository redisRepository;

    @Mock
    private PaymentEventProducer eventProducer;

    @Mock
    private PaymentRedisRepository paymentRedisRepository; // PaymentRedisRepository로 변경

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processPaymentAsync_test() {
        // 테스트 목표: 초기 상태를 Redis에 저장하고 Kafka 이벤트를 발행하는지 확인
        // Given
        String requestId = "testRequestId";
        int reservationId = 1;
        double amount = 100.0;

        // When
        paymentService.processPaymentAsync(requestId, reservationId, amount);

        // Then
        verify(paymentRedisRepository).savePaymentStatus(eq("payment:status:" + requestId), eq("PENDING"));
        verify(paymentRedisRepository).savePaymentMessage(eq("payment:message:" + requestId), eq("결제 요청 중..."));
        verify(eventProducer).sendPaymentRequestedEvent(
                "requestId=testRequestId,reservationId=1,amount=100.00"
        );
    }

    @Test
    void processPayment_success_test() {
        // 테스트 목표: 결제가 성공적으로 처리되고 상태 및 메시지가 업데이트되는지 확인
        // Given
        String requestId = "testRequestId";
        int reservationId = 1;
        double amount = 100.0;

        Payment payment = new Payment();
        payment.setReservationId(reservationId);
        payment.setAmount(amount);
        payment.setPaymentStatus("PENDING");

        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        // When
        paymentService.processPayment(requestId, reservationId, amount);

        // Then
        verify(paymentRepository, times(2)).save(any(Payment.class));
        verify(paymentRedisRepository).savePaymentStatus("payment:status:" + requestId, "COMPLETED");
        verify(paymentRedisRepository).savePaymentStatus(
                "payment:message:" + requestId, "requestId=testRequestId, Payment completed for reservationId=1"
        );
    }

    @Test
    void processPayment_failure_test() {
        // 테스트 목표: 결제가 실패하면 상태가 실패로 업데이트되고 좌석 상태가 복구되는지 확인
        // Given
        String requestId = "testRequestId";
        int reservationId = 1;
        double amount = 100.0;

        Payment payment = new Payment();
        payment.setReservationId(reservationId);
        payment.setAmount(amount);
        payment.setPaymentStatus("PENDING");

        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        doThrow(new RuntimeException("Test Exception")).when(seatReservationRepository).findById(reservationId);

        // When & Then
        assertThrows(RuntimeException.class, () -> paymentService.processPayment(requestId, reservationId, amount));

        verify(paymentRepository, times(3)).save(any(Payment.class)); // PENDING → FAILED
        verify(paymentRedisRepository).savePaymentStatus("payment:status:" + requestId, "FAILED");
        verify(paymentRedisRepository).savePaymentStatus("payment:message:" + requestId, "결제 실패: Test Exception");
    }
}
