package com.haodaone.auth.repository;

import com.haodaone.auth.entity.MfaRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    Optional<MfaRecoveryCode> findFirstByUser_IdAndCodeHashAndUsedAtIsNull(Long userId, String codeHash);

    long countByUser_IdAndUsedAtIsNull(Long userId);

    @Modifying
    @Query("delete from MfaRecoveryCode c where c.user.id = :userId")
    void deleteAllForUser(@Param("userId") Long userId);
}
