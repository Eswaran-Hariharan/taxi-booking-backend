package com.uber.rider.repository;

import com.uber.rider.entity.Rider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiderRepository extends JpaRepository<Rider, String> {
    Optional<Rider> findByPhone(String phone);
    Optional<Rider> findByEmail(String email);
    Optional<Rider> findByReferralCode(String referralCode);
}
