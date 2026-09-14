package com.haodaone.monitoring.repository;

import com.haodaone.monitoring.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findTopByDevice_IdAndRequestedBy_IdAndUsedFalseAndDeletedFalseOrderByCreatedAtDesc(Long deviceId, Long userId);
}