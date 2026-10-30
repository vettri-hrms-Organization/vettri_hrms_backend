package com.haodaone.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimesheetTaskRepository extends JpaRepository<TimesheetTask, Long> {
    List<TimesheetTask> findAllByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);
    List<TimesheetTask> findAllByProjectIdAndDeletedFalseOrderByNameAsc(Long projectId);
    Optional<TimesheetTask> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
    List<TimesheetTask> findAllByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);
}
