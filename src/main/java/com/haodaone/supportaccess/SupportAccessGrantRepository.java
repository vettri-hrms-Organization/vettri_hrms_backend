package com.haodaone.supportaccess;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SupportAccessGrantRepository extends JpaRepository<SupportAccessGrant, Long> {

    Optional<SupportAccessGrant> findFirstByCompany_IdAndRevokedAtIsNullAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(
            Long companyId, LocalDateTime now);

    @EntityGraph(attributePaths = "company")
    List<SupportAccessGrant> findAllByRevokedAtIsNullAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtAsc(LocalDateTime now);

    List<SupportAccessGrant> findAllByCompany_IdAndRevokedAtIsNullAndDeletedFalse(Long companyId);
}
