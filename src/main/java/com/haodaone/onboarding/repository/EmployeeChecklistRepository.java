package com.haodaone.onboarding.repository;

import com.haodaone.onboarding.entity.EmployeeChecklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeChecklistRepository extends JpaRepository<EmployeeChecklist, Long> {
    List<EmployeeChecklist> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByStartedAtDesc(Long companyId, Long employeeId);

    List<EmployeeChecklist> findAllByCompany_IdAndStatusAndDeletedFalseOrderByStartedAtDesc(Long companyId, String status);

    Optional<EmployeeChecklist> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    boolean existsByCompany_IdAndEmployee_IdAndChecklistTypeAndStatusAndDeletedFalse(
            Long companyId, Long employeeId, String checklistType, String status);
}
