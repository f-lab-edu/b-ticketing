package com.bticketing.main.controller;

import com.bticketing.main.dto.PaymentRequestDto;
import com.bticketing.main.entity.Payment;
import com.bticketing.main.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<String> requestPayment(@RequestBody PaymentRequestDto requestDto) {
        String requestId = UUID.randomUUID().toString();
        paymentService.processPaymentAsync(requestId, requestDto.getReservationId(), requestDto.getAmount());
        return ResponseEntity.ok(requestId);
    }

    @PostMapping("/{requestId}/complete")
    public ResponseEntity<String> completePayment(@PathVariable String requestId, int reservationId, double amount) {
        try {
            // 실제 결제 완료 처리
            paymentService.processPayment(requestId, reservationId, amount);
            return ResponseEntity.ok("결제 완료");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("결제 완료 처리 중 오류: " + e.getMessage());
        }
    }

    @GetMapping("/{requestId}/status")
    public ResponseEntity<String> getPaymentStatus(@PathVariable String requestId) {
        String status = paymentService.getPaymentStatus(requestId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/{requestId}/message")
    public ResponseEntity<String> getPaymentMessage(@PathVariable String requestId) {
        String message = paymentService.getPaymentMessage(requestId);
        return ResponseEntity.ok(message);
    }
}
