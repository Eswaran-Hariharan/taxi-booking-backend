package com.uber.rider.controller;

import com.uber.rider.entity.Rider;
import com.uber.rider.service.RiderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/riders")
@RequiredArgsConstructor
public class RiderController {

    private final RiderService riderService;

    @PostMapping
    public ResponseEntity<Rider> register(@RequestBody Rider rider) {
        return ResponseEntity.ok(riderService.register(rider));
    }

    @GetMapping("/{riderId}")
    public ResponseEntity<Rider> getById(@PathVariable String riderId) {
        return ResponseEntity.ok(riderService.getById(riderId));
    }

    @PutMapping("/{riderId}/payment-method")
    public ResponseEntity<Void> setPaymentMethod(
        @PathVariable String riderId,
        @RequestParam String paymentMethodId
    ) {
        riderService.setDefaultPaymentMethod(riderId, paymentMethodId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{riderId}/rate")
    public ResponseEntity<Rider> rateRider(
        @PathVariable String riderId,
        @RequestParam int rating
    ) {
        return ResponseEntity.ok(riderService.updateRating(riderId, rating));
    }
}
