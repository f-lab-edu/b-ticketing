package com.bticketing.main.controller;

import com.bticketing.main.entity.Payment;
import com.bticketing.main.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public ResponseEntity<Payment> processPayment(
            @RequestParam int reservationId,
            @RequestParam double amount) {
        Payment payment = paymentService.processPayment(reservationId, amount);
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/cancel")
    public ResponseEntity<String> cancelPayment(@RequestParam int reservationId) {
        paymentService.cancelPayment(reservationId);
        return ResponseEntity.ok("결제가 취소되었습니다.");
    }

}
