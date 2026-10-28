package com.haodaone.sso.repository;

import com.haodaone.sso.entity.SsoLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface SsoLoginAttemptRepository extends JpaRepository<SsoLoginAttempt, Long> {

    Optional<SsoLoginAttempt> findByStateHash(String stateHash);

    Optional<SsoLoginAttempt> findByExchangeCodeHash(String exchangeCodeHash);

    @Modifying
    @Query("DELETE FROM SsoLoginAttempt a WHERE a.createdAt < :before")
    int deleteCreatedBefore(@Param("before") LocalDateTime before);
}
