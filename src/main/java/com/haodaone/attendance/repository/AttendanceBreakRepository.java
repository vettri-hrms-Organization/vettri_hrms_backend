package com.haodaone.attendance.repository;

import com.haodaone.attendance.entity.AttendanceBreak;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AttendanceBreakRepository extends JpaRepository<AttendanceBreak, Long> {

    Optional<AttendanceBreak> findFirstByAttendanceSession_IdAndEndedAtIsNullAndDeletedFalse(Long attendanceSessionId);

    List<AttendanceBreak> findAllByAttendanceSession_IdAndDeletedFalseOrderByStartedAtAsc(Long attendanceSessionId);
}
