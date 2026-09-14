package com.haodaone.audit.repository;

import com.haodaone.audit.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    Page<LoginHistory> findAllByOrderByAttemptedAtDesc(Pageable pageable);

    Page<LoginHistory> findByUsernameOrderByAttemptedAtDesc(String username, Pageable pageable);

    long countByUsernameAndSuccessFalseAndAttemptedAtAfter(String username, LocalDateTime after);
}
