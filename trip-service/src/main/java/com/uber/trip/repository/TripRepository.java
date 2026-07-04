package com.uber.trip.repository;

import com.uber.trip.entity.Trip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TripRepository extends JpaRepository<Trip, String> {

    Page<Trip> findByRiderIdOrderByCreatedAtDesc(String riderId, Pageable pageable);

    Page<Trip> findByDriverIdOrderByCreatedAtDesc(String driverId, Pageable pageable);

    Optional<Trip> findByIdAndRiderId(String id, String riderId);

    Optional<Trip> findByIdAndDriverId(String id, String driverId);

    List<Trip> findByStatusIn(List<Trip.TripStatus> statuses);

    @Query("SELECT t FROM Trip t WHERE t.driverId = :driverId AND t.status = 'IN_PROGRESS'")
    Optional<Trip> findActiveDriverTrip(String driverId);

    @Query("SELECT t FROM Trip t WHERE t.riderId = :riderId AND t.status NOT IN ('COMPLETED', 'CANCELLED')")
    Optional<Trip> findActiveRiderTrip(String riderId);

    @Query("SELECT COUNT(t) FROM Trip t WHERE t.driverId = :driverId AND t.status = 'COMPLETED' AND t.createdAt >= :since")
    long countCompletedTrips(String driverId, Instant since);

    @Query("SELECT AVG(t.distanceKm) FROM Trip t WHERE t.driverId = :driverId AND t.status = 'COMPLETED'")
    Double avgDistanceForDriver(String driverId);
}
