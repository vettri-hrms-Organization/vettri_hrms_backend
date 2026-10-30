package com.haodaone.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeFamilyMemberRepository extends JpaRepository<EmployeeFamilyMember, Long> {
    List<EmployeeFamilyMember> findAllByEmployeeIdAndDeletedFalseOrderByIdAsc(Long employeeId);
    Optional<EmployeeFamilyMember> findByIdAndEmployeeIdAndDeletedFalse(Long id, Long employeeId);
}
