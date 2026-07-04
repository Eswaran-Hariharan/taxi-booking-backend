package com.uber.location.service;

import com.uber.common.config.KafkaTopics;
import com.uber.common.events.LocationUpdateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationKafkaConsumer {

    private final GeoSpatialService geoSpatialService;
    private final LocationBroadcastService broadcastService;

    @KafkaListener(
        topics = KafkaTopics.LOCATION_UPDATES,
        groupId = "location-service",
        concurrency = "10"
    )
    public void consumeLocationUpdate(
        LocationUpdateEvent event,
        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition
    ) {
        if (event.getEntityType() == LocationUpdateEvent.EntityType.DRIVER) {
            geoSpatialService.updateDriverLocation(event.getEntityId(), event.getLocation());
            broadcastService.broadcastToTrip(event);
        }
    }
}
