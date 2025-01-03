package com.bticketing.main.service;

import com.bticketing.main.entity.Payment;
import com.bticketing.main.repository.payment.PaymentRepository;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.observability.RedisObservation;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private static Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final SeatRedisRepository redisRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          SeatReservationRepository seatReservationRepository,
                          SeatRedisRepository redisRepository) {
        this.paymentRepository = paymentRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.redisRepository = redisRepository;
    }

    @Transactional
    public Payment processPayment(int reservationId, double amount) {
        Payment payment = createPayment(reservationId, amount);
        Payment savedPayment = paymentRepository.save(payment);

        try {
            updatePaymentStatus(savedPayment, "COMPLETED");
            updateSeatStatus(reservationId, "COMPLETE");
        } catch (Exception e) {
            updatePaymentStatus(savedPayment, "FAILED");
            revertSeatStatusToAvailable(reservationId);

            throw new RuntimeException("결제 처리 중 오류가 발생했습니다.");
        }
        return savedPayment;
    }

    @Transactional
    public void cancelPayment(int reservationId) {

        updateSeatStatus(reservationId, "AVAILABLE");
        paymentRepository.findById(reservationId).ifPresent(payment -> {
            updatePaymentStatus(payment, "CANCELED");
        });

    }

    private void updatePaymentStatus(Payment payment, String status) {
        payment.setPaymentStatus(status);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);
    }

    private void updateSeatStatus(int reservationId, String status) {
        seatReservationRepository.findById(reservationId).ifPresent(reservation -> {
            reservation.setStatus(status);
            seatReservationRepository.save(reservation);

            String redisKey = String.format("seat:%d:%d",
                    reservation.getScheduleId(), reservation.getSeat().getSeatId());
            redisRepository.setSeatStatus(redisKey, status);

        });
    }

    private void revertSeatStatusToAvailable(int reservationId) {
        updateSeatStatus(reservationId, "AVAILABLE");
    }

    private Payment createPayment(int reservationId, double amount) {
        Payment payment = new Payment();
        payment.setReservationId(reservationId);
        payment.setPaymentStatus("PENDING");
        payment.setAmount(amount);
        payment.setPaymentDate(LocalDateTime.now());
        return payment;
    }
}

