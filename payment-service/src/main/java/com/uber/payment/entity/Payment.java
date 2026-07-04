package com.uber.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_trip", columnList = "trip_id"),
    @Index(name = "idx_payment_rider", columnList = "rider_id"),
    @Index(name = "idx_payment_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    private String id;

    @Column(name = "trip_id", nullable = false)
    private String tripId;

    @Column(name = "rider_id", nullable = false)
    private String riderId;

    @Column(name = "driver_id", nullable = false)
    private String driverId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "driver_payout", precision = 10, scale = 2)
    private BigDecimal driverPayout;

    @Column(name = "platform_fee", precision = 10, scale = 2)
    private BigDecimal platformFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "payment_method_id")
    private String paymentMethodId;

    @Column(name = "gateway_transaction_id")
    private String gatewayTransactionId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "failure_reason")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public enum PaymentStatus {
        INITIATED, AUTHORIZED, CAPTURED, FAILED, REFUNDED, PARTIALLY_REFUNDED
    }

    public enum PaymentMethod {
        CARD, UPI, WALLET, CASH, CORPORATE
    }
}
