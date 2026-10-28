package com.haodaone.privacy.repository;

import com.haodaone.privacy.entity.CompanyDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CompanyDeletionRequestRepository extends JpaRepository<CompanyDeletionRequest, Long> {

    Optional<CompanyDeletionRequest> findFirstByCompany_IdAndStatusAndDeletedFalseOrderByRequestedAtDesc(Long companyId, String status);

    List<CompanyDeletionRequest> findAllByCompany_IdAndDeletedFalseOrderByRequestedAtDesc(Long companyId);

    List<CompanyDeletionRequest> findAllByStatusAndScheduledForBeforeAndDeletedFalse(String status, LocalDateTime cutoff);

    List<CompanyDeletionRequest> findAllByDeletedFalseOrderByRequestedAtDesc();
}
