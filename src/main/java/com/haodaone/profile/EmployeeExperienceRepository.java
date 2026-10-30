package com.haodaone.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeExperienceRepository extends JpaRepository<EmployeeExperience, Long> {
    List<EmployeeExperience> findAllByEmployeeIdAndDeletedFalseOrderByStartDateDescIdDesc(Long employeeId);
    Optional<EmployeeExperience> findByIdAndEmployeeIdAndDeletedFalse(Long id, Long employeeId);
}
