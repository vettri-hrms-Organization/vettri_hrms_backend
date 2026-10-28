package com.haodaone.attendance.shift.repository;

import com.haodaone.attendance.shift.entity.OvertimeEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OvertimeEntryRepository extends JpaRepository<OvertimeEntry, Long> {

    /** Includes soft-deleted rows (employee_id + work_date is unique). */
    Optional<OvertimeEntry> findByEmployee_IdAndWorkDate(Long employeeId, LocalDate workDate);

    @EntityGraph(attributePaths = {"employee", "reviewedByEmployee"})
    Optional<OvertimeEntry> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    @EntityGraph(attributePaths = {"employee", "reviewedByEmployee"})
    List<OvertimeEntry> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByWorkDateDesc(Long companyId, Long employeeId);

    @EntityGraph(attributePaths = {"employee", "reviewedByEmployee"})
    List<OvertimeEntry> findAllByCompany_IdAndDeletedFalseAndWorkDateBetweenOrderByWorkDateDesc(
            Long companyId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"employee", "reviewedByEmployee"})
    List<OvertimeEntry> findAllByCompany_IdAndEmployee_IdInAndDeletedFalseAndWorkDateBetweenOrderByWorkDateDesc(
            Long companyId, Collection<Long> employeeIds, LocalDate from, LocalDate to);

    List<OvertimeEntry> findAllByCompany_IdAndEmployee_IdAndStatusAndPayrollItemIdIsNullAndDeletedFalseAndWorkDateLessThanEqualOrderByWorkDateAsc(
            Long companyId, Long employeeId, String status, LocalDate upTo);

    List<OvertimeEntry> findAllByPayrollItemIdAndDeletedFalse(Long payrollItemId);
}
