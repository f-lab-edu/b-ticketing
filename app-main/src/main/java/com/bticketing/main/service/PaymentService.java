package com.bticketing.main.service;

import com.bticketing.main.entity.Payment;
import com.bticketing.main.kafka.producer.PaymentEventProducer;
import com.bticketing.main.repository.payment.PaymentRepository;
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
    private final RedisTemplate<String, String> redisTemplate;

    private static final String STATUS_KEY_PREFIX = "payment:status:";
    private static final String MESSAGE_KEY_PREFIX = "payment:message:";

    public PaymentService(PaymentRepository paymentRepository,
                          SeatReservationRepository seatReservationRepository,
                          SeatRedisRepository redisRepository,
                          PaymentEventProducer eventProducer,
                          RedisTemplate<String, String> redisTemplate) {
        this.paymentRepository = paymentRepository;
        this.seatReservationRepository = seatReservationRepository;
        this.redisRepository = redisRepository;
        this.eventProducer = eventProducer;
        this.redisTemplate = redisTemplate;
    }

    public void processPaymentAsync(String requestId, int reservationId, double amount) {
        // 초기 상태 저장
        redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, "PENDING");
        redisTemplate.opsForValue().set(MESSAGE_KEY_PREFIX + requestId, "결제 요청 중...");

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

            // Kafka 완료 이벤트 발행
            String completionMessage = String.format("requestId=%s, Payment completed for reservationId=%d", requestId, reservationId);
            redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, "COMPLETED");
            redisTemplate.opsForValue().set(MESSAGE_KEY_PREFIX + requestId, completionMessage);

        } catch (Exception e) {
            updatePaymentStatus(savedPayment, "FAILED");
            revertSeatStatusToAvailable(reservationId);

            redisTemplate.opsForValue().set(STATUS_KEY_PREFIX + requestId, "FAILED");
            redisTemplate.opsForValue().set(MESSAGE_KEY_PREFIX + requestId, "결제 실패: " + e.getMessage());

            throw new RuntimeException("결제 처리 중 오류가 발생했습니다.");
        }
        return savedPayment;
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

    public String getPaymentStatus(String requestId) {
        return redisTemplate.opsForValue().get(STATUS_KEY_PREFIX + requestId);
    }

    public String getPaymentMessage(String requestId) {
        return redisTemplate.opsForValue().get(MESSAGE_KEY_PREFIX + requestId);
    }

}
