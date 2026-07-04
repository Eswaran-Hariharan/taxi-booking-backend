package com.uber.payment.repository;

import com.uber.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, String> {
    Optional<Payment> findByTripId(String tripId);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
