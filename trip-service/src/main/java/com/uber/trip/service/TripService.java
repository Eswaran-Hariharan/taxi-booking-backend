package com.uber.trip.service;

import com.uber.common.config.KafkaTopics;
import com.uber.common.dto.LocationDto;
import com.uber.common.events.*;
import com.uber.trip.entity.Trip;
import com.uber.trip.entity.Trip.TripStatus;
import com.uber.trip.repository.TripRepository;
import com.uber.trip.statemachine.TripStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TripService {

    private static final double BASE_FARE = 2.50;
    private static final double PER_KM_RATE = 1.20;
    private static final double PER_MINUTE_RATE = 0.25;

    private final TripRepository tripRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final FareCalculationService fareCalculationService;

    @Transactional
    public Trip createTrip(String riderId, LocationDto pickup, LocationDto dropoff, String vehicleType) {
        tripRepository.findActiveRiderTrip(riderId).ifPresent(t -> {
            throw new IllegalStateException("Rider already has an active trip: " + t.getId());
        });

        Trip trip = Trip.builder()
            .id(UUID.randomUUID().toString())
            .riderId(riderId)
            .status(TripStatus.REQUESTED)
            .pickupLat(pickup.getLatitude())
            .pickupLon(pickup.getLongitude())
            .dropoffLat(dropoff.getLatitude())
            .dropoffLon(dropoff.getLongitude())
            .vehicleType(vehicleType)
            .fareEstimate(fareCalculationService.estimateFare(pickup, dropoff, 1.0))
            .surgeMultiplier(1.0)
            .build();

        trip = tripRepository.save(trip);

        TripRequestEvent event = TripRequestEvent.builder()
            .tripId(trip.getId())
            .riderId(riderId)
            .pickupLocation(pickup)
            .dropoffLocation(dropoff)
            .vehicleType(vehicleType)
            .requestedAt(Instant.now())
            .build();

        kafkaTemplate.send(KafkaTopics.TRIP_REQUESTS, trip.getId(), event);
        return trip;
    }

    @KafkaListener(topics = KafkaTopics.DRIVER_MATCHED, groupId = "trip-service")
    @Transactional
    public void handleDriverMatched(DriverMatchedEvent event) {
        tripRepository.findById(event.getTripId()).ifPresent(trip -> {
            TripStateMachine.transition(trip, TripStatus.DRIVER_ASSIGNED);
            trip.setDriverId(event.getDriverId());
            trip.setFareEstimate(BigDecimal.valueOf(event.getFareEstimate()));
            tripRepository.save(trip);

            publishTripStatus(trip, event.getDriverLocation());
        });
    }

    @Transactional
    public Trip updateStatus(String tripId, String actorId, TripStatus newStatus) {
        Trip trip = tripRepository.findById(tripId)
            .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));

        TripStateMachine.transition(trip, newStatus);

        switch (newStatus) {
            case DRIVER_ARRIVED -> trip.setDriverArrivedAt(Instant.now());
            case IN_PROGRESS -> trip.setTripStartedAt(Instant.now());
            case PAYMENT_PENDING -> {
                trip.setTripEndedAt(Instant.now());
                BigDecimal fare = fareCalculationService.calculateActualFare(trip);
                trip.setActualFare(fare);
            }
            case COMPLETED -> publishCompletionEvents(trip);
            default -> {}
        }

        trip = tripRepository.save(trip);
        publishTripStatus(trip, null);
        return trip;
    }

    @Transactional
    public Trip cancelTrip(String tripId, String cancelledBy, String reason) {
        Trip trip = tripRepository.findById(tripId)
            .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));

        TripStateMachine.transition(trip, TripStatus.CANCELLED);
        trip.setCancelledBy(cancelledBy);
        trip.setCancellationReason(reason);
        trip = tripRepository.save(trip);

        publishTripStatus(trip, null);
        return trip;
    }

    @Transactional
    public void rateTrip(String tripId, String raterId, int rating, String raterRole) {
        Trip trip = tripRepository.findById(tripId)
            .orElseThrow(() -> new RuntimeException("Trip not found: " + tripId));

        if ("RIDER".equals(raterRole)) {
            trip.setDriverRating(rating);
        } else {
            trip.setRiderRating(rating);
        }
        tripRepository.save(trip);
    }

    private void publishTripStatus(Trip trip, LocationDto location) {
        TripStatusEvent event = TripStatusEvent.builder()
            .tripId(trip.getId())
            .driverId(trip.getDriverId())
            .riderId(trip.getRiderId())
            .status(TripStatusEvent.TripStatus.valueOf(trip.getStatus().name()))
            .currentLocation(location)
            .timestamp(Instant.now())
            .build();

        kafkaTemplate.send(KafkaTopics.TRIP_STATUS, trip.getId(), event);
    }

    private void publishCompletionEvents(Trip trip) {
        PaymentEvent paymentEvent = PaymentEvent.builder()
            .paymentId(UUID.randomUUID().toString())
            .tripId(trip.getId())
            .riderId(trip.getRiderId())
            .driverId(trip.getDriverId())
            .amount(trip.getActualFare())
            .status(PaymentEvent.PaymentStatus.INITIATED)
            .processedAt(Instant.now())
            .build();

        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, trip.getId(), paymentEvent);
    }
}
