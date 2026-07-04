package com.uber.location.controller;

import com.uber.common.dto.LocationDto;
import com.uber.location.service.GeoSpatialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/location")
@RequiredArgsConstructor
public class LocationController {

    private final GeoSpatialService geoSpatialService;

    @PutMapping("/drivers/{driverId}")
    public ResponseEntity<Void> updateDriverLocation(
        @PathVariable String driverId,
        @RequestBody LocationDto location
    ) {
        geoSpatialService.updateDriverLocation(driverId, location);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<LocationDto> getDriverLocation(@PathVariable String driverId) {
        return geoSpatialService.getDriverLocation(driverId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/drivers/nearby")
    public ResponseEntity<List<GeoSpatialService.NearbyDriver>> findNearbyDrivers(
        @RequestParam double lat,
        @RequestParam double lon,
        @RequestParam(defaultValue = "5.0") double radiusKm,
        @RequestParam(defaultValue = "20") int limit
    ) {
        LocationDto location = LocationDto.builder().latitude(lat).longitude(lon).build();
        return ResponseEntity.ok(geoSpatialService.findNearbyDrivers(location, radiusKm, limit));
    }

    @GetMapping("/drivers/geohash")
    public ResponseEntity<Map<String, Object>> findDriversByGeoHash(
        @RequestParam double lat,
        @RequestParam double lon
    ) {
        List<String> driverIds = geoSpatialService.findDriversByGeoHash(lat, lon);
        return ResponseEntity.ok(Map.of("driverIds", driverIds, "count", driverIds.size()));
    }

    @DeleteMapping("/drivers/{driverId}")
    public ResponseEntity<Void> removeDriver(@PathVariable String driverId) {
        geoSpatialService.removeDriver(driverId);
        return ResponseEntity.noContent().build();
    }
}
