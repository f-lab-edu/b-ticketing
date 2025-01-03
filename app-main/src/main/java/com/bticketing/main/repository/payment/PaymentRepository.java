package com.bticketing.main.repository.payment;

import com.bticketing.main.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Integer> {

    List<Payment> findByReservationId(String reservationId);

    List<Payment> findByPaymentStatus(String paymentStatus);
}
