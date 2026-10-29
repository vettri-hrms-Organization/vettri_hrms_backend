package com.haodaone.monitoring.repository;

import com.haodaone.monitoring.entity.AgentRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentReleaseRepository extends JpaRepository<AgentRelease, Long> {

    Optional<AgentRelease> findFirstByActiveTrueAndDeletedFalseOrderByCreatedAtDesc();

    List<AgentRelease> findAllByDeletedFalseOrderByCreatedAtDesc();

    boolean existsByReleaseVersionAndDeletedFalse(String releaseVersion);

    Optional<AgentRelease> findByIdAndDeletedFalse(Long id);
}
