package com.haodaone.software.repository;

import com.haodaone.software.entity.SoftwareDeploymentTarget;
import com.haodaone.software.entity.SoftwareDeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SoftwareDeploymentTargetRepository extends JpaRepository<SoftwareDeploymentTarget, Long> {
    List<SoftwareDeploymentTarget> findByDeployment_IdAndDeletedFalseOrderByIdAsc(Long deploymentId);
    List<SoftwareDeploymentTarget> findByDevice_IdAndDeletedFalseOrderByIdAsc(Long deviceId);
    Optional<SoftwareDeploymentTarget> findByIdAndDevice_IdAndDeletedFalse(Long id, Long deviceId);
    Optional<SoftwareDeploymentTarget> findByDeployment_IdAndDevice_IdAndDeletedFalse(Long deploymentId, Long deviceId);
    List<SoftwareDeploymentTarget> findByDevice_Company_IdAndStatusInAndDeletedFalse(Long companyId, List<SoftwareDeploymentStatus> statuses);
}
