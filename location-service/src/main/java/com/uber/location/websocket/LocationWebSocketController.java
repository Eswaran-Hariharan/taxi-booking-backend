package com.uber.location.websocket;

import com.uber.common.dto.LocationDto;
import com.uber.common.events.LocationUpdateEvent;
import com.uber.location.service.GeoSpatialService;
import com.uber.location.service.LocationBroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;

@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final GeoSpatialService geoSpatialService;
    private final LocationBroadcastService broadcastService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/driver/location")
    public void handleDriverLocationUpdate(LocationUpdateEvent event, Principal principal) {
        event.setEntityId(principal.getName());
        event.setEntityType(LocationUpdateEvent.EntityType.DRIVER);
        event.setTimestamp(Instant.now());

        geoSpatialService.updateDriverLocation(event.getEntityId(), event.getLocation());
        broadcastService.broadcastToTrip(event);
    }

    @MessageMapping("/rider/track/{tripId}")
    public void subscribeToTripTracking(@DestinationVariable String tripId, Principal principal) {
        broadcastService.addTripSubscriber(tripId, principal.getName());
    }

    @SubscribeMapping("/driver/location/{driverId}")
    public LocationDto getDriverCurrentLocation(@DestinationVariable String driverId) {
        return geoSpatialService.getDriverLocation(driverId).orElse(null);
    }

    public void pushLocationToRider(String riderId, LocationDto location) {
        messagingTemplate.convertAndSendToUser(riderId, "/queue/driver-location", location);
    }

    public void pushTripUpdate(String tripId, Object update) {
        messagingTemplate.convertAndSend("/topic/trip/" + tripId, update);
    }
}
