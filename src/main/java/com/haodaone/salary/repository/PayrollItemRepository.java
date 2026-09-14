package com.haodaone.salary.repository;

import com.haodaone.salary.entity.PayrollItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface PayrollItemRepository extends JpaRepository<PayrollItem, Long> {

    List<PayrollItem> findByPayrollRun_Company_IdAndPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(
            Long companyId,
            Long payrollRunId
    );

    Optional<PayrollItem> findByIdAndPayrollRun_Company_IdAndDeletedFalse(
            Long id,
            Long companyId
    );

    Optional<PayrollItem> findByIdAndDeletedFalse(Long id);

    List<PayrollItem> findByPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(
            Long payrollRunId
    );

    Optional<PayrollItem> findByPayrollRunIdAndEmployeeIdAndDeletedFalse(
            Long payrollRunId,
            Long employeeId
    );

    List<PayrollItem> findByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(
            Long employeeId
    );

    /**
     * Finds the most recent payroll items for a specific employee
     * belonging to a specific company.
     */
    List<PayrollItem> findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(
            Long companyId,
            Long employeeId
    );

    long countByPayrollRunIdAndDeletedFalse(Long payrollRunId);

    long countByPayrollRunIdAndStatusAndDeletedFalse(
            Long payrollRunId,
            String status
    );

    /**
     * The most recent payroll line per employee, across all runs.
     * Drives the "Payroll Status" / "Last Payroll Date" columns
     * on the salary list.
     */
    @Query("""
            select i
            from PayrollItem i
            where i.deleted = false
              and i.payrollRun.company.id = :companyId
              and i.id in (
                  select max(i2.id)
                  from PayrollItem i2
                  where i2.deleted = false
                    and i2.payrollRun.company.id = :companyId
                  group by i2.employee.id
              )
            """)
    List<PayrollItem> findLatestPerEmployee(
            @Param("companyId") Long companyId
    );

    default Map<Long, PayrollItem> findLatestByEmployeeId(Long companyId) {
        return findLatestPerEmployee(companyId)
                .stream()
                .collect(Collectors.toMap(
                        i -> i.getEmployee().getId(),
                        i -> i
                ));
    }

    @Query("""
            select coalesce(sum(i.netSalary), 0)
            from PayrollItem i
            where i.payrollRun.id = :runId
              and i.deleted = false
              and i.status <> 'ON_HOLD'
            """)
    BigDecimal sumNetSalaryForRun(
            @Param("runId") Long runId
    );

    @Query("""
            select coalesce(sum(i.grossSalary), 0)
            from PayrollItem i
            where i.payrollRun.id = :runId
              and i.deleted = false
              and i.status <> 'ON_HOLD'
            """)
    BigDecimal sumGrossSalaryForRun(
            @Param("runId") Long runId
    );

    @Query("""
            select coalesce(sum(i.totalDeductions), 0)
            from PayrollItem i
            where i.payrollRun.id = :runId
              and i.deleted = false
              and i.status <> 'ON_HOLD'
            """)
    BigDecimal sumDeductionsForRun(
            @Param("runId") Long runId
    );
}