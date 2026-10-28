package com.haodaone.auth.repository;

import com.haodaone.auth.entity.MfaChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, Long> {

    Optional<MfaChallenge> findByTokenHash(String tokenHash);
}
