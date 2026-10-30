package com.haodaone.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimesheetWeekRepository extends JpaRepository<TimesheetWeek, Long> {
    Optional<TimesheetWeek> findByEmployeeIdAndWeekStartAndDeletedFalse(Long employeeId, LocalDate weekStart);
    Optional<TimesheetWeek> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
    List<TimesheetWeek> findAllByCompanyIdAndStatusAndDeletedFalseOrderByWeekStartAscIdAsc(Long companyId, String status);
    List<TimesheetWeek> findAllByCompanyIdAndWeekStartAndDeletedFalse(Long companyId, LocalDate weekStart);
    List<TimesheetWeek> findAllByCompanyIdAndWeekStartBetweenAndDeletedFalse(Long companyId, LocalDate from, LocalDate to);
}
