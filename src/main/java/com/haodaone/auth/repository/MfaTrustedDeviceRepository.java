package com.haodaone.auth.repository;

import com.haodaone.auth.entity.MfaTrustedDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MfaTrustedDeviceRepository extends JpaRepository<MfaTrustedDevice, Long> {

    Optional<MfaTrustedDevice> findByTokenHash(String tokenHash);

    long countByUser_IdAndRevokedAtIsNullAndExpiresAtAfter(Long userId, LocalDateTime now);

    @Modifying
    @Query("update MfaTrustedDevice d set d.revokedAt = :now where d.user.id = :userId and d.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
