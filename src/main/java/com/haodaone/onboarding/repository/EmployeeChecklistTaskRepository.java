package com.haodaone.onboarding.repository;

import com.haodaone.onboarding.entity.EmployeeChecklistTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeChecklistTaskRepository extends JpaRepository<EmployeeChecklistTask, Long> {
    Optional<EmployeeChecklistTask> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    /** Pending tasks past due on in-progress checklists, across every company (daily reminder job). */
    @Query("select t from EmployeeChecklistTask t join fetch t.checklist c join fetch c.employee "
            + "where t.deleted = false and c.deleted = false and t.status = 'PENDING' and c.status = 'IN_PROGRESS' "
            + "and t.dueDate is not null and t.dueDate < :today")
    List<EmployeeChecklistTask> findOverdue(@Param("today") LocalDate today);

    /** Pending tasks on in-progress checklists for one company (HR dashboard / "my tasks"). */
    @Query("select t from EmployeeChecklistTask t join fetch t.checklist c join fetch c.employee "
            + "where t.deleted = false and c.deleted = false and t.status = 'PENDING' and c.status = 'IN_PROGRESS' "
            + "and t.company.id = :companyId order by t.dueDate asc")
    List<EmployeeChecklistTask> findOpenByCompany(@Param("companyId") Long companyId);
}
