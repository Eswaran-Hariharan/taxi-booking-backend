package com.uber.trip.service;

import com.uber.common.dto.LocationDto;
import com.uber.trip.entity.Trip;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Service
public class FareCalculationService {

    private static final double BASE_FARE = 2.50;
    private static final double PER_KM_RATE = 1.20;
    private static final double PER_MIN_RATE = 0.25;
    private static final double MIN_FARE = 5.00;
    private static final double BOOKING_FEE = 1.75;
    private static final double SERVICE_FEE_PERCENT = 0.05;

    public BigDecimal estimateFare(LocationDto pickup, LocationDto dropoff, double surgeMultiplier) {
        double distanceKm = pickup.distanceTo(dropoff) / 1000.0;
        double estimatedMinutes = distanceKm / 30.0 * 60;
        return calculateFare(distanceKm, estimatedMinutes, surgeMultiplier);
    }

    public BigDecimal calculateActualFare(Trip trip) {
        if (trip.getTripStartedAt() == null || trip.getTripEndedAt() == null) {
            return trip.getFareEstimate();
        }
        double durationMinutes = Duration.between(trip.getTripStartedAt(), trip.getTripEndedAt()).toMinutes();
        return calculateFare(trip.getDistanceKm(), durationMinutes, trip.getSurgeMultiplier());
    }

    private BigDecimal calculateFare(double distanceKm, double durationMinutes, double surge) {
        double fare = BASE_FARE + (distanceKm * PER_KM_RATE) + (durationMinutes * PER_MIN_RATE);
        fare = Math.max(fare, MIN_FARE);
        fare *= surge;
        fare += BOOKING_FEE;
        fare += fare * SERVICE_FEE_PERCENT;
        return BigDecimal.valueOf(fare).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateCancellationFee(Trip trip, Instant cancelledAt) {
        if (trip.getStatus() == Trip.TripStatus.REQUESTED) return BigDecimal.ZERO;
        if (trip.getDriverArrivedAt() == null) return BigDecimal.ZERO;
        long waitMinutes = Duration.between(trip.getDriverArrivedAt(), cancelledAt).toMinutes();
        if (waitMinutes < 2) return BigDecimal.ZERO;
        return BigDecimal.valueOf(5.00);
    }

    public record FareBreakdown(
        BigDecimal baseFare,
        BigDecimal distanceFare,
        BigDecimal timeFare,
        BigDecimal surgeFare,
        BigDecimal bookingFee,
        BigDecimal serviceFee,
        BigDecimal total
    ) {}

    public FareBreakdown getBreakdown(double distanceKm, double durationMinutes, double surge) {
        BigDecimal base = BigDecimal.valueOf(BASE_FARE);
        BigDecimal distance = BigDecimal.valueOf(distanceKm * PER_KM_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal time = BigDecimal.valueOf(durationMinutes * PER_MIN_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subTotal = base.add(distance).add(time);
        BigDecimal surgeAdd = subTotal.multiply(BigDecimal.valueOf(surge - 1)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal booking = BigDecimal.valueOf(BOOKING_FEE);
        BigDecimal service = subTotal.add(surgeAdd).multiply(BigDecimal.valueOf(SERVICE_FEE_PERCENT)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subTotal.add(surgeAdd).add(booking).add(service);
        return new FareBreakdown(base, distance, time, surgeAdd, booking, service, total);
    }
}
