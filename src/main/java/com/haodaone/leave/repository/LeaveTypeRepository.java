package com.haodaone.leave.repository;

import com.haodaone.leave.entity.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {
    /** Tenant-scoped: only company leave types. Global queries not allowed for security. */
    List<LeaveType> findAllByCompany_IdAndDeletedFalseOrderByNameAsc(Long companyId);
    Optional<LeaveType> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
    boolean existsByCompany_IdAndCode(Long companyId, String code);
}
