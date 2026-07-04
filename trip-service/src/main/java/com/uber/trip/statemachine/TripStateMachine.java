package com.uber.trip.statemachine;

import com.uber.trip.entity.Trip;
import com.uber.trip.entity.Trip.TripStatus;

import java.util.*;

public class TripStateMachine {

    private static final Map<TripStatus, Set<TripStatus>> VALID_TRANSITIONS = new EnumMap<>(TripStatus.class);

    static {
        VALID_TRANSITIONS.put(TripStatus.REQUESTED, Set.of(TripStatus.DRIVER_ASSIGNED, TripStatus.CANCELLED));
        VALID_TRANSITIONS.put(TripStatus.DRIVER_ASSIGNED, Set.of(TripStatus.DRIVER_ARRIVING, TripStatus.CANCELLED));
        VALID_TRANSITIONS.put(TripStatus.DRIVER_ARRIVING, Set.of(TripStatus.DRIVER_ARRIVED, TripStatus.CANCELLED));
        VALID_TRANSITIONS.put(TripStatus.DRIVER_ARRIVED, Set.of(TripStatus.IN_PROGRESS, TripStatus.CANCELLED));
        VALID_TRANSITIONS.put(TripStatus.IN_PROGRESS, Set.of(TripStatus.PAYMENT_PENDING, TripStatus.COMPLETED));
        VALID_TRANSITIONS.put(TripStatus.PAYMENT_PENDING, Set.of(TripStatus.COMPLETED));
        VALID_TRANSITIONS.put(TripStatus.COMPLETED, Collections.emptySet());
        VALID_TRANSITIONS.put(TripStatus.CANCELLED, Collections.emptySet());
    }

    public static void transition(Trip trip, TripStatus newStatus) {
        Set<TripStatus> allowed = VALID_TRANSITIONS.getOrDefault(trip.getStatus(), Collections.emptySet());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                "Invalid transition from " + trip.getStatus() + " to " + newStatus + " for trip " + trip.getId()
            );
        }
        trip.setStatus(newStatus);
    }

    public static boolean canTransition(TripStatus current, TripStatus next) {
        return VALID_TRANSITIONS.getOrDefault(current, Collections.emptySet()).contains(next);
    }
}
