package com.uber.location.service;

import com.uber.common.dto.GeoHashUtil;
import com.uber.common.dto.LocationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class GeoSpatialService {

    private static final String DRIVER_GEO_KEY = "drivers:geo";
    private static final String DRIVER_LOCATION_PREFIX = "driver:location:";
    private static final String DRIVER_GEOHASH_PREFIX = "geohash:drivers:";
    private static final int GEOHASH_PRECISION = 6;
    private static final long DRIVER_LOCATION_TTL_SECONDS = 30;

    private final RedisTemplate<String, Object> redisTemplate;

    public void updateDriverLocation(String driverId, LocationDto location) {
        GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
        geoOps.add(DRIVER_GEO_KEY,
            new Point(location.getLongitude(), location.getLatitude()),
            driverId);

        String locationKey = DRIVER_LOCATION_PREFIX + driverId;
        redisTemplate.opsForHash().put(locationKey, "lat", String.valueOf(location.getLatitude()));
        redisTemplate.opsForHash().put(locationKey, "lon", String.valueOf(location.getLongitude()));
        redisTemplate.opsForHash().put(locationKey, "ts", String.valueOf(location.getTimestamp()));
        redisTemplate.expire(locationKey, Duration.ofSeconds(DRIVER_LOCATION_TTL_SECONDS));

        String oldGeoHash = (String) redisTemplate.opsForHash().get(locationKey, "geohash");
        String newGeoHash = GeoHashUtil.encode(location.getLatitude(), location.getLongitude(), GEOHASH_PRECISION);

        if (!newGeoHash.equals(oldGeoHash)) {
            if (oldGeoHash != null) {
                redisTemplate.opsForSet().remove(DRIVER_GEOHASH_PREFIX + oldGeoHash, driverId);
            }
            redisTemplate.opsForSet().add(DRIVER_GEOHASH_PREFIX + newGeoHash, driverId);
            redisTemplate.opsForHash().put(locationKey, "geohash", newGeoHash);
        }
    }

    public List<NearbyDriver> findNearbyDrivers(LocationDto riderLocation, double radiusKm, int limit) {
        GeoOperations<String, Object> geoOps = redisTemplate.opsForGeo();
        Circle circle = new Circle(
            new Point(riderLocation.getLongitude(), riderLocation.getLatitude()),
            new Distance(radiusKm, Metrics.KILOMETERS)
        );

        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
            .newGeoRadiusArgs()
            .includeCoordinates()
            .includeDistance()
            .sortAscending()
            .limit(limit);

        GeoResults<RedisGeoCommands.GeoLocation<Object>> results = geoOps.radius(DRIVER_GEO_KEY, circle, args);

        List<NearbyDriver> drivers = new ArrayList<>();
        if (results != null) {
            for (GeoResult<RedisGeoCommands.GeoLocation<Object>> result : results) {
                String driverId = result.getContent().getName().toString();
                Point point = result.getContent().getPoint();
                double distanceKm = result.getDistance().getValue();

                LocationDto driverLoc = LocationDto.builder()
                    .latitude(point.getY())
                    .longitude(point.getX())
                    .timestamp(System.currentTimeMillis())
                    .build();

                drivers.add(new NearbyDriver(driverId, driverLoc, distanceKm));
            }
        }
        return drivers;
    }

    public List<String> findDriversByGeoHash(double latitude, double longitude) {
        String geoHash = GeoHashUtil.encode(latitude, longitude, GEOHASH_PRECISION);
        String[] neighbors = GeoHashUtil.neighbors(geoHash);
        Set<String> driverIds = new HashSet<>();

        Set<Object> members = redisTemplate.opsForSet().members(DRIVER_GEOHASH_PREFIX + geoHash);
        if (members != null) members.forEach(m -> driverIds.add(m.toString()));

        for (String neighbor : neighbors) {
            Set<Object> neighborMembers = redisTemplate.opsForSet().members(DRIVER_GEOHASH_PREFIX + neighbor);
            if (neighborMembers != null) neighborMembers.forEach(m -> driverIds.add(m.toString()));
        }

        return new ArrayList<>(driverIds);
    }

    public Optional<LocationDto> getDriverLocation(String driverId) {
        String locationKey = DRIVER_LOCATION_PREFIX + driverId;
        Map<Object, Object> data = redisTemplate.opsForHash().entries(locationKey);
        if (data.isEmpty()) return Optional.empty();

        return Optional.of(LocationDto.builder()
            .latitude(Double.parseDouble(data.get("lat").toString()))
            .longitude(Double.parseDouble(data.get("lon").toString()))
            .timestamp(Long.parseLong(data.get("ts").toString()))
            .build());
    }

    public void removeDriver(String driverId) {
        String locationKey = DRIVER_LOCATION_PREFIX + driverId;
        String geoHash = (String) redisTemplate.opsForHash().get(locationKey, "geohash");
        if (geoHash != null) {
            redisTemplate.opsForSet().remove(DRIVER_GEOHASH_PREFIX + geoHash, driverId);
        }
        redisTemplate.opsForGeo().remove(DRIVER_GEO_KEY, driverId);
        redisTemplate.delete(locationKey);
    }

    public record NearbyDriver(String driverId, LocationDto location, double distanceKm) {}
}
