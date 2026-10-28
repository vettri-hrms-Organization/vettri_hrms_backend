package com.haodaone.audit.repository;

import com.haodaone.audit.entity.LoginHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    Page<LoginHistory> findAllByOrderByAttemptedAtDesc(Pageable pageable);

    /** Attempts whose typed login matches a username or email of a user in this company. */
    @Query("""
            select h from LoginHistory h
            where exists (select 1 from User u where u.company.id = :companyId
                          and (lower(u.username) = lower(h.username) or lower(u.email) = lower(h.username)))
            order by h.attemptedAt desc
            """)
    Page<LoginHistory> findForCompany(@Param("companyId") Long companyId, Pageable pageable);

    Page<LoginHistory> findByUsernameOrderByAttemptedAtDesc(String username, Pageable pageable);

    long countByUsernameAndSuccessFalseAndAttemptedAtAfter(String username, LocalDateTime after);
}
