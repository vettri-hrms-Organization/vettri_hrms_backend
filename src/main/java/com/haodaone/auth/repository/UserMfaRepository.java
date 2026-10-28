package com.haodaone.auth.repository;

import com.haodaone.auth.entity.UserMfa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserMfaRepository extends JpaRepository<UserMfa, Long> {

    Optional<UserMfa> findByUser_IdAndDeletedFalse(Long userId);
}
