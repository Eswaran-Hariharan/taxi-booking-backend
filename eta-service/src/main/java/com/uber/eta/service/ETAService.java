package com.uber.eta.service;

import com.uber.common.dto.LocationDto;
import com.uber.eta.algorithm.ETAEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class ETAService {

    private static final String ETA_CACHE_PREFIX = "eta:cache:";
    private static final long ETA_CACHE_TTL_SECONDS = 60;
    private static final double ETA_CACHE_GRID_SIZE = 0.001;

    private final ETAEngine etaEngine;
    private final RedisTemplate<String, Object> redisTemplate;

    public ETAEngine.ETAResult getETA(LocationDto origin, LocationDto destination) {
        String cacheKey = buildCacheKey(origin, destination);
        Object cached = redisTemplate.opsForValue().get(ETA_CACHE_PREFIX + cacheKey);
        if (cached != null) {
            return parseETAResult(cached.toString());
        }

        ETAEngine.ETAResult result = etaEngine.calculateETA(origin, destination);
        redisTemplate.opsForValue().set(
            ETA_CACHE_PREFIX + cacheKey,
            result.etaMinutes() + "," + result.distanceKm() + "," + result.etaSeconds(),
            Duration.ofSeconds(ETA_CACHE_TTL_SECONDS)
        );
        return result;
    }

    public ETAEngine.ETAResult getLiveETA(String tripId, LocationDto currentLocation, LocationDto destination) {
        ETAEngine.ETAResult result = etaEngine.calculateETA(currentLocation, destination);
        redisTemplate.opsForHash().put("trip:eta:" + tripId, "etaMinutes", String.valueOf(result.etaMinutes()));
        redisTemplate.opsForHash().put("trip:eta:" + tripId, "etaSeconds", String.valueOf(result.etaSeconds()));
        redisTemplate.opsForHash().put("trip:eta:" + tripId, "distanceKm", String.valueOf(result.distanceKm()));
        return result;
    }

    private String buildCacheKey(LocationDto origin, LocationDto destination) {
        double oLat = Math.round(origin.getLatitude() / ETA_CACHE_GRID_SIZE) * ETA_CACHE_GRID_SIZE;
        double oLon = Math.round(origin.getLongitude() / ETA_CACHE_GRID_SIZE) * ETA_CACHE_GRID_SIZE;
        double dLat = Math.round(destination.getLatitude() / ETA_CACHE_GRID_SIZE) * ETA_CACHE_GRID_SIZE;
        double dLon = Math.round(destination.getLongitude() / ETA_CACHE_GRID_SIZE) * ETA_CACHE_GRID_SIZE;
        return oLat + ":" + oLon + ":" + dLat + ":" + dLon;
    }

    private ETAEngine.ETAResult parseETAResult(String value) {
        String[] parts = value.split(",");
        return new ETAEngine.ETAResult(
            Integer.parseInt(parts[0]),
            Double.parseDouble(parts[1]),
            Integer.parseInt(parts[2])
        );
    }
}


@RestController
@RequestMapping("/api/eta")
@RequiredArgsConstructor
class ETAController {

    private final ETAService etaService;

    @PostMapping("/estimate")
    public ETAEngine.ETAResult getEstimate(
        @RequestParam double originLat, @RequestParam double originLon,
        @RequestParam double destLat, @RequestParam double destLon
    ) {
        LocationDto origin = LocationDto.builder().latitude(originLat).longitude(originLon).build();
        LocationDto destination = LocationDto.builder().latitude(destLat).longitude(destLon).build();
        return etaService.getETA(origin, destination);
    }

    @PostMapping("/live/{tripId}")
    public ETAEngine.ETAResult getLiveETA(
        @PathVariable String tripId,
        @RequestParam double currentLat, @RequestParam double currentLon,
        @RequestParam double destLat, @RequestParam double destLon
    ) {
        LocationDto current = LocationDto.builder().latitude(currentLat).longitude(currentLon).build();
        LocationDto destination = LocationDto.builder().latitude(destLat).longitude(destLon).build();
        return etaService.getLiveETA(tripId, current, destination);
    }
}
