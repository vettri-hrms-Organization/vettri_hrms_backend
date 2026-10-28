package com.haodaone.auth.repository;

import com.haodaone.auth.entity.MfaEmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MfaEmailOtpRepository extends JpaRepository<MfaEmailOtp, Long> {

    Optional<MfaEmailOtp> findTopByUser_IdAndPurposeOrderBySentAtDescIdDesc(Long userId, String purpose);

    Optional<MfaEmailOtp> findTopByChallenge_IdOrderBySentAtDescIdDesc(Long challengeId);
}
