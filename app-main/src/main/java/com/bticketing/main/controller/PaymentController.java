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
