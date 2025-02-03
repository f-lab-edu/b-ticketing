package com.bticketing.main.service;

import com.bticketing.main.entity.Payment;
import com.bticketing.main.kafka.producer.PaymentEventProducer;
import com.bticketing.main.repository.payment.PaymentRepository;
import com.bticketing.main.repository.redis.PaymentRedisRepository;
import com.bticketing.main.repository.redis.SeatRedisRepository;
import com.bticketing.main.repository.seat.SeatReservationRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final SeatReservationRepository seatReservationRepository;
    private final SeatRedisRepository redisRepository;
    private final PaymentEventProducer eventProducer;
    private final PaymentRedisRepository paymentRedisRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          SeatReservationRepository seatReservationRepository,
                          SeatRedisRepository redisRepository,
                          PaymentEventProducer eventProducer,
                          PaymentRedisRepository paymentRedisRepository) {
        this.paymentRepository = paymentRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.redisRepository = redisRepository;
        this.eventProducer = eventProducer;
        this.paymentRedisRepository = paymentRedisRepository;
    }

    public void processPaymentAsync(String requestId, int reservationId, double amount) {
        paymentRedisRepository.savePaymentStatus(requestId, "PENDING");
        paymentRedisRepository.savePaymentMessage(requestId, "결제 요청 중...");

        logger.info("Saved initial payment status to Redis: requestId={}, status=PENDING", requestId);

        // Kafka 이벤트 발행
        String message = String.format("requestId=%s,reservationId=%d,amount=%.2f", requestId, reservationId, amount);
        eventProducer.sendPaymentRequestedEvent(message);
    }


    @Transactional
    public Payment processPayment(String requestId, int reservationId, double amount) {
        Payment payment = createPayment(reservationId, amount);
        Payment savedPayment = paymentRepository.save(payment);

        try {
            updatePaymentStatus(savedPayment, "COMPLETED");
            updateSeatStatus(reservationId, "COMPLETE");

            String completionMessage = String.format("결제 완료: ReservationId=%d, 금액=%.2f", reservationId, amount);
            paymentRedisRepository.savePaymentStatus(requestId, "COMPLETED");
            paymentRedisRepository.savePaymentMessage(requestId, completionMessage);

        } catch (Exception e) {
            updatePaymentStatus(savedPayment, "FAILED");
            revertSeatStatusToAvailable(reservationId);

            paymentRedisRepository.savePaymentStatus(requestId, "FAILED");
            paymentRedisRepository.savePaymentMessage(requestId, "결제 실패: " + e.getMessage());

            throw new RuntimeException("결제 처리 중 오류가 발생했습니다.");
        }
        return savedPayment;
    }

    public String getPaymentStatus(String requestId) {
        return paymentRedisRepository.getPaymentStatus(requestId);
    }

    public String getPaymentMessage(String requestId) {
        return paymentRedisRepository.getPaymentMessage(requestId);
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

            String redisKey = String.format("seat:%d:%d", reservation.getScheduleId(), reservation.getSeat().getSeatId());
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
