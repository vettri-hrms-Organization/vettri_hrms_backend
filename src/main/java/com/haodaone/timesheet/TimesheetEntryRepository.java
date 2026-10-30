package com.haodaone.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimesheetEntryRepository extends JpaRepository<TimesheetEntry, Long> {
    List<TimesheetEntry> findAllByEmployeeIdAndWorkDateBetweenAndDeletedFalseOrderByWorkDateAscIdAsc(Long employeeId, LocalDate from, LocalDate to);
    List<TimesheetEntry> findAllByCompanyIdAndWorkDateBetweenAndDeletedFalse(Long companyId, LocalDate from, LocalDate to);
    Optional<TimesheetEntry> findByIdAndEmployeeIdAndDeletedFalse(Long id, Long employeeId);
    List<TimesheetEntry> findAllByCompanyIdAndProjectIdAndDeletedFalse(Long companyId, Long projectId);
    boolean existsByEmployeeIdAndWorkDateAndDeletedFalse(Long employeeId, LocalDate workDate);
}
