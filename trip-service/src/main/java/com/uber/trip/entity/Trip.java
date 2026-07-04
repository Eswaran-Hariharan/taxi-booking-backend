package com.uber.trip.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trips", indexes = {
    @Index(name = "idx_trip_rider", columnList = "rider_id"),
    @Index(name = "idx_trip_driver", columnList = "driver_id"),
    @Index(name = "idx_trip_status", columnList = "status"),
    @Index(name = "idx_trip_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trip {

    @Id
    private String id;

    @Column(name = "rider_id", nullable = false)
    private String riderId;

    @Column(name = "driver_id")
    private String driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TripStatus status;

    @Column(name = "pickup_lat")
    private double pickupLat;

    @Column(name = "pickup_lon")
    private double pickupLon;

    @Column(name = "pickup_address")
    private String pickupAddress;

    @Column(name = "dropoff_lat")
    private double dropoffLat;

    @Column(name = "dropoff_lon")
    private double dropoffLon;

    @Column(name = "dropoff_address")
    private String dropoffAddress;

    @Column(name = "vehicle_type")
    private String vehicleType;

    @Column(name = "fare_estimate", precision = 10, scale = 2)
    private BigDecimal fareEstimate;

    @Column(name = "actual_fare", precision = 10, scale = 2)
    private BigDecimal actualFare;

    @Column(name = "surge_multiplier")
    private double surgeMultiplier;

    @Column(name = "distance_km")
    private double distanceKm;

    @Column(name = "duration_minutes")
    private int durationMinutes;

    @Column(name = "driver_arrived_at")
    private Instant driverArrivedAt;

    @Column(name = "trip_started_at")
    private Instant tripStartedAt;

    @Column(name = "trip_ended_at")
    private Instant tripEndedAt;

    @Column(name = "rider_rating")
    private Integer riderRating;

    @Column(name = "driver_rating")
    private Integer driverRating;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum TripStatus {
        REQUESTED, DRIVER_ASSIGNED, DRIVER_ARRIVING, DRIVER_ARRIVED,
        IN_PROGRESS, COMPLETED, CANCELLED, PAYMENT_PENDING
    }
}
