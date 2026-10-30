package com.haodaone.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimesheetProjectRepository extends JpaRepository<TimesheetProject, Long> {
    List<TimesheetProject> findAllByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);
    Optional<TimesheetProject> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
    List<TimesheetProject> findAllByCompanyIdAndIdIn(Long companyId, Collection<Long> ids);
}
