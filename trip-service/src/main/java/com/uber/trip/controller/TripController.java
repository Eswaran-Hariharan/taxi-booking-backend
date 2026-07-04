package com.uber.trip.controller;

import com.uber.common.dto.LocationDto;
import com.uber.trip.entity.Trip;
import com.uber.trip.service.TripService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @PostMapping
    public ResponseEntity<Trip> createTrip(@RequestBody CreateTripRequest request) {
        LocationDto pickup = LocationDto.builder()
            .latitude(request.pickupLat()).longitude(request.pickupLon()).build();
        LocationDto dropoff = LocationDto.builder()
            .latitude(request.dropoffLat()).longitude(request.dropoffLon()).build();

        Trip trip = tripService.createTrip(request.riderId(), pickup, dropoff, request.vehicleType());
        return ResponseEntity.ok(trip);
    }

    @PatchMapping("/{tripId}/status")
    public ResponseEntity<Trip> updateStatus(
        @PathVariable String tripId,
        @RequestBody StatusUpdateRequest request
    ) {
        Trip trip = tripService.updateStatus(tripId, request.actorId(), Trip.TripStatus.valueOf(request.status()));
        return ResponseEntity.ok(trip);
    }

    @PostMapping("/{tripId}/cancel")
    public ResponseEntity<Trip> cancelTrip(
        @PathVariable String tripId,
        @RequestBody CancelRequest request
    ) {
        Trip trip = tripService.cancelTrip(tripId, request.cancelledBy(), request.reason());
        return ResponseEntity.ok(trip);
    }

    @PostMapping("/{tripId}/rate")
    public ResponseEntity<Void> rateTrip(
        @PathVariable String tripId,
        @RequestBody RatingRequest request
    ) {
        tripService.rateTrip(tripId, request.raterId(), request.rating(), request.raterRole());
        return ResponseEntity.noContent().build();
    }

    public record CreateTripRequest(
        String riderId, double pickupLat, double pickupLon,
        double dropoffLat, double dropoffLon, String vehicleType
    ) {}

    public record StatusUpdateRequest(String actorId, String status) {}
    public record CancelRequest(String cancelledBy, String reason) {}
    public record RatingRequest(String raterId, int rating, String raterRole) {}
}
