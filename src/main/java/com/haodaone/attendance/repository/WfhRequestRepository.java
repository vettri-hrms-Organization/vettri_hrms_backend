package com.haodaone.attendance.repository;

import com.haodaone.attendance.entity.WfhRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WfhRequestRepository extends JpaRepository<WfhRequest, Long> {
    Optional<WfhRequest> findByEmployee_IdAndCompany_IdAndWorkDateAndDeletedFalse(Long employeeId, Long companyId, LocalDate workDate);

    List<WfhRequest> findAllByCompany_IdAndDeletedFalseOrderByWorkDateDesc(Long companyId);

    List<WfhRequest> findAllByEmployee_IdAndDeletedFalseOrderByWorkDateDesc(Long employeeId);

    List<WfhRequest> findAllByEmployee_IdAndCompany_IdAndDeletedFalseOrderByWorkDateDesc(Long employeeId, Long companyId);

    List<WfhRequest> findAllByCompany_IdAndStatusAndDeletedFalseOrderByWorkDateDesc(Long companyId, String status);
}
