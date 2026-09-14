package com.haodaone.audit.repository;

import com.haodaone.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByOrderByPerformedAtDesc(Pageable pageable);

    Page<AuditLog> findByEntityNameOrderByPerformedAtDesc(String entityName, Pageable pageable);

    Page<AuditLog> findByCompany_IdOrderByPerformedAtDesc(Long companyId, Pageable pageable);

    Page<AuditLog> findByCompany_IdAndEntityNameOrderByPerformedAtDesc(Long companyId, String entityName, Pageable pageable);

    /** Used by modules (e.g. Salary's "Recent Payroll Activity") whose entries span more than one entityName. */
    Page<AuditLog> findByEntityNameInOrderByPerformedAtDesc(Collection<String> entityNames, Pageable pageable);

    @Query("select a from AuditLog a where a.entityName in :entityNames and a.company.id = :companyId order by a.performedAt desc")
    Page<AuditLog> findByCompanyIdAndEntityNameInOrderByPerformedAtDesc(@Param("companyId") Long companyId,
                                                                          @Param("entityNames") Set<String> entityNames,
                                                                          Pageable pageable);

    /**
     * A single entity's full history, unpaginated - powers the Candidate
     * Timeline (RECRUITMENT_VIEW-scoped, not AUDIT_VIEW - see
     * CandidateController#timeline). A candidate's own history is small
     * enough that pagination isn't worth the added complexity here, unlike
     * the org-wide audit log this repository otherwise serves.
     */
    List<AuditLog> findByEntityNameAndEntityIdOrderByPerformedAtDesc(String entityName, Long entityId);
}
