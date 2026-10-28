package com.haodaone.settlement.repository;

import com.haodaone.settlement.entity.FinalSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FinalSettlementRepository extends JpaRepository<FinalSettlement, Long> {

    Optional<FinalSettlement> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    List<FinalSettlement> findAllByCompany_IdAndDeletedFalseOrderByIdDesc(Long companyId);

    List<FinalSettlement> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByIdDesc(Long companyId, Long employeeId);

    boolean existsByCompany_IdAndEmployee_IdAndStatusInAndDeletedFalse(Long companyId, Long employeeId, Collection<String> statuses);

    /** Employees with an APPROVED or PAID settlement - excluded from later payroll runs. */
    @Query("""
            select distinct s.employee.id
            from FinalSettlement s
            where s.company.id = :companyId
              and s.deleted = false
              and s.status in ('APPROVED', 'PAID')
            """)
    List<Long> findSettledEmployeeIds(@Param("companyId") Long companyId);
}
