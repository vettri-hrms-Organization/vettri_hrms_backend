package com.haodaone.org.repository;

import com.haodaone.org.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    /** Tenant-scoped: only company departments. Global queries not allowed for security. */
    List<Department> findAllByCompany_IdAndDeletedFalseOrderByNameAsc(Long companyId);
    Optional<Department> findByCode(String code);
    Optional<Department> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
    boolean existsByCode(String code);
    boolean existsByCompany_IdAndCodeAndDeletedFalse(Long companyId, String code);
}
