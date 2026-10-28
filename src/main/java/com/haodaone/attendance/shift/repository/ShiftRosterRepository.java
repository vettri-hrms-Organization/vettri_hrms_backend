package com.haodaone.attendance.shift.repository;

import com.haodaone.attendance.shift.entity.ShiftRoster;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShiftRosterRepository extends JpaRepository<ShiftRoster, Long> {

    @EntityGraph(attributePaths = {"shift", "employee"})
    List<ShiftRoster> findAllByCompany_IdAndDeletedFalseAndRosterDateBetween(Long companyId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = {"shift", "employee"})
    List<ShiftRoster> findAllByCompany_IdAndEmployee_IdInAndDeletedFalseAndRosterDateBetween(
            Long companyId, Collection<Long> employeeIds, LocalDate from, LocalDate to);

    /** Includes soft-deleted rows so a cleared cell can be reused (employee_id + roster_date is unique). */
    Optional<ShiftRoster> findByEmployee_IdAndRosterDate(Long employeeId, LocalDate rosterDate);

    boolean existsByShift_IdAndDeletedFalseAndRosterDateGreaterThanEqual(Long shiftId, LocalDate from);
}
