package com.haodaone.attendance.repository;

import com.haodaone.attendance.entity.WorkSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WorkSessionRepository extends JpaRepository<WorkSession, Long> {
    Optional<WorkSession> findByEmployee_IdAndStatusAndSessionDate(Long employeeId, String status, LocalDate sessionDate);

    List<WorkSession> findAllByCompany_IdAndSessionDateOrderByLoginTimeDesc(Long companyId, LocalDate sessionDate);

    List<WorkSession> findAllByCompany_IdAndWorkingModeAndSessionDateOrderByLoginTimeDesc(Long companyId, String workingMode, LocalDate sessionDate);

    List<WorkSession> findAllByCompany_IdAndWorkingModeAndSessionDateBetween(Long companyId, String workingMode, LocalDate startDate, LocalDate endDate);

    long countByEmployee_IdAndStatusAndSessionDate(Long employeeId, String status, LocalDate sessionDate);
}
