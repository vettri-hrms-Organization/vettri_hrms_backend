package com.haodaone.requirements.repository;

import com.haodaone.requirements.entity.Requirement;
import com.haodaone.requirements.entity.RequirementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RequirementRepository extends JpaRepository<Requirement, Long> {
    List<Requirement> findAllByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    List<Requirement> findAllByCompany_IdAndStatusAndDeletedFalseOrderByCreatedAtDesc(Long companyId, RequirementStatus status);
    Optional<Requirement> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
}