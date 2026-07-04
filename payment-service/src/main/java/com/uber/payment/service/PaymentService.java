package com.uber.payment.service;

import com.uber.common.config.KafkaTopics;
import com.uber.common.events.PaymentEvent;
import com.uber.payment.entity.Payment;
import com.uber.payment.gateway.PaymentGateway;
import com.uber.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final double DRIVER_PAYOUT_PERCENT = 0.80;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final String PAYMENT_LOCK_PREFIX = "payment:lock:";
    private static final String RIDER_PAYMENT_METHOD_PREFIX = "rider:payment:";

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = "payment-service")
    @Transactional
    public void handlePaymentEvent(PaymentEvent event) {
        if (event.getStatus() != PaymentEvent.PaymentStatus.INITIATED) return;

        String idempotencyKey = "trip:" + event.getTripId() + ":payment";
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Payment already processed for trip {}", event.getTripId());
            return;
        }

        String lockKey = PAYMENT_LOCK_PREFIX + event.getTripId();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("Payment already being processed for trip {}", event.getTripId());
            return;
        }

        try {
            processPayment(event, idempotencyKey);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void processPayment(PaymentEvent event, String idempotencyKey) {
        BigDecimal driverPayout = event.getAmount()
            .multiply(BigDecimal.valueOf(DRIVER_PAYOUT_PERCENT))
            .setScale(2, RoundingMode.HALF_UP);
        BigDecimal platformFee = event.getAmount().subtract(driverPayout);

        Payment payment = Payment.builder()
            .id(UUID.randomUUID().toString())
            .tripId(event.getTripId())
            .riderId(event.getRiderId())
            .driverId(event.getDriverId())
            .amount(event.getAmount())
            .driverPayout(driverPayout)
            .platformFee(platformFee)
            .status(Payment.PaymentStatus.INITIATED)
            .idempotencyKey(idempotencyKey)
            .retryCount(0)
            .build();

        payment = paymentRepository.save(payment);
        attemptCharge(payment);
    }

    private void attemptCharge(Payment payment) {
        String riderPaymentMethod = getRiderPaymentMethod(payment.getRiderId());
        if (riderPaymentMethod == null) {
            failPayment(payment, "NO_PAYMENT_METHOD");
            return;
        }

        PaymentGateway.ChargeResult result = paymentGateway.charge(
            payment.getRiderId(),
            riderPaymentMethod,
            payment.getAmount(),
            payment.getIdempotencyKey()
        );

        if (result.success()) {
            payment.setStatus(Payment.PaymentStatus.CAPTURED);
            payment.setGatewayTransactionId(result.transactionId());
            payment.setProcessedAt(Instant.now());
            paymentRepository.save(payment);
            publishPaymentResult(payment, PaymentEvent.PaymentStatus.CAPTURED);
            log.info("Payment captured for trip {}: {}", payment.getTripId(), result.transactionId());
        } else {
            payment.setRetryCount(payment.getRetryCount() + 1);
            if (payment.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                paymentRepository.save(payment);
                scheduleRetry(payment);
            } else {
                failPayment(payment, result.failureCode());
            }
        }
    }

    private void scheduleRetry(Payment payment) {
        log.info("Scheduling retry {} for payment {}", payment.getRetryCount(), payment.getId());
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, payment.getTripId(),
            PaymentEvent.builder()
                .paymentId(payment.getId())
                .tripId(payment.getTripId())
                .riderId(payment.getRiderId())
                .driverId(payment.getDriverId())
                .amount(payment.getAmount())
                .status(PaymentEvent.PaymentStatus.INITIATED)
                .processedAt(Instant.now())
                .build()
        );
    }

    private void failPayment(Payment payment, String reason) {
        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        payment.setProcessedAt(Instant.now());
        paymentRepository.save(payment);
        publishPaymentResult(payment, PaymentEvent.PaymentStatus.FAILED);
        log.error("Payment failed for trip {}: {}", payment.getTripId(), reason);
    }

    @Transactional
    public Payment refundPayment(String paymentId, BigDecimal refundAmount) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        PaymentGateway.RefundResult result = paymentGateway.refund(
            payment.getGatewayTransactionId(), refundAmount
        );

        if (result.success()) {
            boolean isPartial = refundAmount.compareTo(payment.getAmount()) < 0;
            payment.setStatus(isPartial ? Payment.PaymentStatus.PARTIALLY_REFUNDED : Payment.PaymentStatus.REFUNDED);
            payment = paymentRepository.save(payment);
            publishPaymentResult(payment, PaymentEvent.PaymentStatus.REFUNDED);
        }
        return payment;
    }

    private void publishPaymentResult(Payment payment, PaymentEvent.PaymentStatus status) {
        PaymentEvent event = PaymentEvent.builder()
            .paymentId(payment.getId())
            .tripId(payment.getTripId())
            .riderId(payment.getRiderId())
            .driverId(payment.getDriverId())
            .amount(payment.getAmount())
            .status(status)
            .processedAt(Instant.now())
            .build();

        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, payment.getTripId(), event);
    }

    private String getRiderPaymentMethod(String riderId) {
        Object method = redisTemplate.opsForValue().get(RIDER_PAYMENT_METHOD_PREFIX + riderId);
        return method != null ? method.toString() : null;
    }
}
