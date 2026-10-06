package com.haodaone.attendance.repository;

import com.haodaone.attendance.entity.AttendanceSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    @EntityGraph(attributePaths = "employee")
    Optional<AttendanceSession> findTopByEmployee_IdAndCompany_IdAndAttendanceDateAndStatusInOrderByCheckInTimeDesc(
            Long employeeId, Long companyId, LocalDate attendanceDate, List<String> statuses);

    @EntityGraph(attributePaths = "employee")
    List<AttendanceSession> findAllByCompany_IdAndAttendanceDateOrderByCheckInTimeDesc(Long companyId, LocalDate attendanceDate);

    @EntityGraph(attributePaths = "employee")
    List<AttendanceSession> findAllByCompany_IdAndEmployee_IdInAndAttendanceDateOrderByCheckInTimeDesc(
            Long companyId, java.util.Collection<Long> employeeIds, LocalDate attendanceDate);

    @EntityGraph(attributePaths = "employee")
    List<AttendanceSession> findAllByCompany_IdAndEmployee_IdOrderByAttendanceDateDesc(Long companyId, Long employeeId);

    @EntityGraph(attributePaths = "employee")
    List<AttendanceSession> findAllByCompany_IdAndEmployee_IdAndDeletedFalseAndAttendanceDateBetweenOrderByAttendanceDateDescCheckInTimeDesc(
            Long companyId, Long employeeId, LocalDate from, LocalDate to);

    Optional<AttendanceSession> findByEmployee_IdAndCompany_IdAndStatusAndAttendanceDate(Long employeeId, Long companyId, String status, LocalDate attendanceDate);
}
