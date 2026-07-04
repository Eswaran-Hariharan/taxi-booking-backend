package com.uber.location.service;

import com.uber.common.events.LocationUpdateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class LocationBroadcastService {

    private static final String TRIP_SUBSCRIBERS_PREFIX = "trip:subscribers:";
    private static final String TRIP_DRIVER_PREFIX = "trip:driver:";

    private final SimpMessagingTemplate messagingTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    public void addTripSubscriber(String tripId, String userId) {
        redisTemplate.opsForSet().add(TRIP_SUBSCRIBERS_PREFIX + tripId, userId);
    }

    public void removeTripSubscriber(String tripId, String userId) {
        redisTemplate.opsForSet().remove(TRIP_SUBSCRIBERS_PREFIX + tripId, userId);
    }

    public void mapDriverToTrip(String driverId, String tripId) {
        redisTemplate.opsForValue().set(TRIP_DRIVER_PREFIX + driverId, tripId);
    }

    public void broadcastToTrip(LocationUpdateEvent event) {
        String tripId = (String) redisTemplate.opsForValue().get(TRIP_DRIVER_PREFIX + event.getEntityId());
        if (tripId == null) return;

        Set<Object> subscribers = redisTemplate.opsForSet().members(TRIP_SUBSCRIBERS_PREFIX + tripId);
        if (subscribers == null) return;

        for (Object subscriber : subscribers) {
            messagingTemplate.convertAndSendToUser(
                subscriber.toString(),
                "/queue/driver-location",
                event.getLocation()
            );
        }

        messagingTemplate.convertAndSend("/topic/trip/" + tripId + "/location", event.getLocation());
    }
}
