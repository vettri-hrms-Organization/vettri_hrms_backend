package com.haodaone.org.repository;

import com.haodaone.org.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    /** Tenant-scoped: only company teams. Global queries not allowed for security. */
    List<Team> findAllByCompany_IdAndDeletedFalseOrderByNameAsc(Long companyId);
    Optional<Team> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
    boolean existsByCompany_IdAndNameAndDeletedFalse(Long companyId, String name);
}
