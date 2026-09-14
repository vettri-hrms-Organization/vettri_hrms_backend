package com.haodaone.software.repository;

import com.haodaone.software.entity.SoftwareDeployment;
import com.haodaone.software.entity.SoftwareDeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SoftwareDeploymentRepository extends JpaRepository<SoftwareDeployment, Long> {
    List<SoftwareDeployment> findByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    Optional<SoftwareDeployment> findByIdAndDeletedFalse(Long id);
    Optional<SoftwareDeployment> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
    List<SoftwareDeployment> findByCompany_IdAndStatusInAndDeletedFalse(Long companyId, List<SoftwareDeploymentStatus> statuses);
}
