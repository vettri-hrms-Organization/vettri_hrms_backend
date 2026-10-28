package com.haodaone.leave.repository;

import com.haodaone.leave.entity.CompOffRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompOffRequestRepository extends JpaRepository<CompOffRequest, Long> {

    @EntityGraph(attributePaths = {"employee", "employee.department", "decidedBy"})
    Optional<CompOffRequest> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "decidedBy"})
    List<CompOffRequest> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByWorkDateDesc(Long companyId, Long employeeId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "decidedBy"})
    List<CompOffRequest> findAllByCompany_IdAndDeletedFalseOrderByWorkDateDesc(Long companyId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "decidedBy"})
    List<CompOffRequest> findAllByCompany_IdAndEmployee_IdInAndDeletedFalseOrderByWorkDateDesc(Long companyId, Collection<Long> employeeIds);

    List<CompOffRequest> findAllByCompany_IdAndEmployee_IdAndStatusAndDeletedFalseOrderByExpiresOnAsc(Long companyId, Long employeeId, String status);

    boolean existsByCompany_IdAndEmployee_IdAndWorkDateAndStatusInAndDeletedFalse(Long companyId, Long employeeId, LocalDate workDate, Collection<String> statuses);
}
