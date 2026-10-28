package com.haodaone.attendance.shift.repository;

import com.haodaone.attendance.shift.entity.ShiftAssignment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    @EntityGraph(attributePaths = {"shift", "department", "employee"})
    List<ShiftAssignment> findAllByCompany_IdAndDeletedFalseOrderByEffectiveFromDesc(Long companyId);

    Optional<ShiftAssignment> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    boolean existsByShift_IdAndDeletedFalse(Long shiftId);
}
