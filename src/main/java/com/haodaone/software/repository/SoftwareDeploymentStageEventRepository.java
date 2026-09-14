package com.haodaone.software.repository;

import com.haodaone.software.entity.SoftwareDeploymentStageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SoftwareDeploymentStageEventRepository extends JpaRepository<SoftwareDeploymentStageEvent, Long> {
    List<SoftwareDeploymentStageEvent> findByTarget_IdOrderByOccurredAtAscIdAsc(Long targetId);
}
