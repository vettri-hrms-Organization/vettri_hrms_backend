package com.haodaone.attendance.repository;

import com.haodaone.attendance.entity.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {
    Optional<AttendanceSession> findTopByEmployee_IdAndCompany_IdAndAttendanceDateAndStatusInOrderByCheckInTimeDesc(
            Long employeeId, Long companyId, LocalDate attendanceDate, List<String> statuses);

    List<AttendanceSession> findAllByCompany_IdAndAttendanceDateOrderByCheckInTimeDesc(Long companyId, LocalDate attendanceDate);

    List<AttendanceSession> findAllByCompany_IdAndEmployee_IdOrderByAttendanceDateDesc(Long companyId, Long employeeId);

    List<AttendanceSession> findAllByCompany_IdAndEmployee_IdInAndAttendanceDateOrderByCheckInTimeDesc(Long companyId, List<Long> employeeIds, LocalDate attendanceDate);

    Optional<AttendanceSession> findByEmployee_IdAndCompany_IdAndStatusAndAttendanceDate(Long employeeId, Long companyId, String status, LocalDate attendanceDate);
}
