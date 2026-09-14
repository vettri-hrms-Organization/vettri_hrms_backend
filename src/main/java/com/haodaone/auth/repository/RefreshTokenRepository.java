package com.haodaone.auth.repository;

import com.haodaone.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedFalse(Long userId);

    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true, rt.revokedAt = CURRENT_TIMESTAMP where rt.user.id = :userId and rt.revoked = false")
    void revokeAllForUser(@Param("userId") Long userId);
}
