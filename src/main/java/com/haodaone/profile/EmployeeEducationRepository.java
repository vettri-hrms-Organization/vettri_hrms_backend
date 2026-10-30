package com.haodaone.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeEducationRepository extends JpaRepository<EmployeeEducation, Long> {
    List<EmployeeEducation> findAllByEmployeeIdAndDeletedFalseOrderByEndYearDescIdDesc(Long employeeId);
    Optional<EmployeeEducation> findByIdAndEmployeeIdAndDeletedFalse(Long id, Long employeeId);
}
