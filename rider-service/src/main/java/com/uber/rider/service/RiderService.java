package com.uber.rider.service;

import com.uber.common.events.TripStatusEvent;
import com.uber.rider.entity.Rider;
import com.uber.rider.repository.RiderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiderService {

    private final RiderRepository riderRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public Rider register(Rider rider) {
        rider.setId(UUID.randomUUID().toString());
        rider.setReferralCode(generateReferralCode(rider.getName()));
        return riderRepository.save(rider);
    }

    @Transactional
    public Rider updateRating(String riderId, int newRating) {
        Rider rider = getById(riderId);
        double updated = ((rider.getAverageRating() * rider.getTotalTrips()) + newRating)
            / (rider.getTotalTrips() + 1);
        rider.setAverageRating(Math.round(updated * 10.0) / 10.0);
        return riderRepository.save(rider);
    }

    @Transactional
    public void setDefaultPaymentMethod(String riderId, String paymentMethodId) {
        Rider rider = getById(riderId);
        rider.setDefaultPaymentMethodId(paymentMethodId);
        riderRepository.save(rider);
        redisTemplate.opsForValue().set("rider:payment:" + riderId, paymentMethodId);
    }

    @KafkaListener(topics = "trip.status", groupId = "rider-service")
    @Transactional
    public void onTripStatusChange(TripStatusEvent event) {
        if (event.getStatus() == TripStatusEvent.TripStatus.COMPLETED) {
            Rider rider = riderRepository.findById(event.getRiderId()).orElse(null);
            if (rider != null) {
                rider.setTotalTrips(rider.getTotalTrips() + 1);
                riderRepository.save(rider);
            }
        }
    }

    public Rider getById(String riderId) {
        return riderRepository.findById(riderId)
            .orElseThrow(() -> new RuntimeException("Rider not found: " + riderId));
    }

    private String generateReferralCode(String name) {
        String prefix = name.replaceAll("[^A-Za-z]", "").toUpperCase();
        prefix = prefix.length() >= 4 ? prefix.substring(0, 4) : prefix;
        return prefix + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
