package com.haodaone.salary.repository;

import com.haodaone.salary.entity.SalaryStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, Long> {
    Optional<SalaryStructure> findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(Long companyId, Long employeeId);
    List<SalaryStructure> findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByEffectiveFromDescCreatedAtDesc(Long companyId, Long employeeId);

    Optional<SalaryStructure> findByIdAndDeletedFalse(Long id);

    Optional<SalaryStructure> findByEmployeeIdAndActiveTrueAndDeletedFalse(Long employeeId);

    List<SalaryStructure> findByEmployeeIdAndDeletedFalseOrderByEffectiveFromDescCreatedAtDesc(Long employeeId);

    /** Every employee's currently active structure at once - the base dataset for the salary list, dashboard KPIs and department distribution. */
    @Query("select s from SalaryStructure s where s.active = true and s.deleted = false")
    List<SalaryStructure> findAllActive();

    @Query("select s from SalaryStructure s where s.active = true and s.deleted = false and s.company.id = :companyId")
    List<SalaryStructure> findAllActiveByCompany(@Param("companyId") Long companyId);

    /** employeeId -> active structure, for attaching salary figures to a page of employees without one query per row. */
    default Map<Long, SalaryStructure> findAllActiveByEmployeeId(Long companyId) {
        return findAllActiveByCompany(companyId).stream().collect(Collectors.toMap(s -> s.getEmployee().getId(), s -> s));
    }

    @Query("select count(s) from SalaryStructure s where s.active = true and s.deleted = false and s.company.id = :companyId")
    long countActive(@Param("companyId") Long companyId);

    @Query("select coalesce(avg(s.netSalary), 0) from SalaryStructure s where s.active = true and s.deleted = false and s.company.id = :companyId")
    BigDecimal averageActiveNetSalary(@Param("companyId") Long companyId);

    @Query("select coalesce(max(s.netSalary), 0) from SalaryStructure s where s.active = true and s.deleted = false and s.company.id = :companyId")
    BigDecimal maxActiveNetSalary(@Param("companyId") Long companyId);

    @Query("select coalesce(min(s.netSalary), 0) from SalaryStructure s where s.active = true and s.deleted = false and s.company.id = :companyId")
    BigDecimal minActiveNetSalary(@Param("companyId") Long companyId);

    @Query("select coalesce(sum(s.netSalary), 0) from SalaryStructure s where s.active = true and s.deleted = false and s.company.id = :companyId")
    BigDecimal sumActiveNetSalary(@Param("companyId") Long companyId);

    /** [departmentName, headcount, totalNetSalary] for every department with at least one actively-paid employee. */
    @Query("select coalesce(d.name, 'Unassigned'), count(s), coalesce(sum(s.netSalary), 0) " +
            "from SalaryStructure s left join s.employee.department d " +
                "where s.active = true and s.deleted = false and s.company.id = :companyId " +
            "group by d.name order by sum(s.netSalary) desc")
            List<Object[]> sumActiveNetSalaryByDepartment(@Param("companyId") Long companyId);

    @Query("select count(s) from SalaryStructure s where s.employee.id = :employeeId and s.deleted = false")
    long countByEmployeeId(@Param("employeeId") Long employeeId);
}
