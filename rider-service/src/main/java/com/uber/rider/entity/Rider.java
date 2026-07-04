package com.uber.rider.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "riders", indexes = {
    @Index(name = "idx_rider_phone", columnList = "phone"),
    @Index(name = "idx_rider_email", columnList = "email")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Rider {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(unique = true)
    private String email;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    @Column(name = "average_rating")
    private double averageRating = 5.0;

    @Column(name = "total_trips")
    private int totalTrips;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "default_payment_method_id")
    private String defaultPaymentMethodId;

    @Column(name = "is_active")
    private boolean isActive = true;

    @Column(name = "referral_code", unique = true)
    private String referralCode;

    @Column(name = "home_address")
    private String homeAddress;

    @Column(name = "work_address")
    private String workAddress;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;
}
