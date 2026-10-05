package com.haodaone.attendance.repository;

import com.haodaone.attendance.entity.AttendanceRegularization;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AttendanceRegularizationRepository extends JpaRepository<AttendanceRegularization, Long> {
    List<AttendanceRegularization> findAllByEmployee_IdAndCompany_IdAndDeletedFalseOrderByAttendanceDateDesc(
            Long employeeId, Long companyId);

    @EntityGraph(attributePaths = "employee")
    List<AttendanceRegularization> findAllByCompany_IdAndStatusAndDeletedFalseOrderByAttendanceDateAsc(
            Long companyId, String status);

    List<AttendanceRegularization> findAllByCompany_IdAndAttendanceDateAndStatusAndDeletedFalseOrderByAttendanceDateAsc(
            Long companyId, LocalDate attendanceDate, String status);

    @EntityGraph(attributePaths = "employee")
    List<AttendanceRegularization> findAllByCompany_IdAndEmployee_IdInAndStatusAndDeletedFalseOrderByAttendanceDateAsc(
            Long companyId, Set<Long> employeeIds, String status);

    @EntityGraph(attributePaths = "employee")
    Optional<AttendanceRegularization> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    boolean existsByEmployee_IdAndCompany_IdAndAttendanceDateAndStatusAndDeletedFalse(
            Long employeeId, Long companyId, LocalDate attendanceDate, String status);

    boolean existsByEmployee_IdAndCompany_IdAndAttendanceDateAndDeletedFalse(
            Long employeeId, Long companyId, LocalDate attendanceDate);
}
