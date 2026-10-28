package com.haodaone.leave.repository;

import com.haodaone.leave.entity.LeaveEncashment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeaveEncashmentRepository extends JpaRepository<LeaveEncashment, Long> {

    @EntityGraph(attributePaths = {"employee", "employee.department", "leaveType", "decidedBy"})
    Optional<LeaveEncashment> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "leaveType", "decidedBy"})
    List<LeaveEncashment> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByCreatedAtDesc(Long companyId, Long employeeId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "leaveType", "decidedBy"})
    List<LeaveEncashment> findAllByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);

    @EntityGraph(attributePaths = {"employee", "leaveType"})
    List<LeaveEncashment> findAllByIdInAndDeletedFalse(Collection<Long> ids);

    /** Days that count as consumed: approved (payable) and already paid. */
    @Query("select coalesce(sum(e.days), 0) from LeaveEncashment e where e.company.id = :companyId and e.employee.id = :employeeId " +
            "and e.leaveType.id = :leaveTypeId and e.leaveYear = :year and e.status in ('APPROVED', 'PAID') and e.deleted = false")
    double sumConsumedDays(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                           @Param("leaveTypeId") Long leaveTypeId, @Param("year") int year);

    @Query("select coalesce(sum(e.days), 0) from LeaveEncashment e where e.company.id = :companyId and e.employee.id = :employeeId " +
            "and e.leaveType.id = :leaveTypeId and e.leaveYear = :year and e.status = 'PENDING' and e.deleted = false")
    double sumPendingDays(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                          @Param("leaveTypeId") Long leaveTypeId, @Param("year") int year);

    @EntityGraph(attributePaths = {"employee", "leaveType"})
    @Query("select e from LeaveEncashment e where e.company.id = :companyId and e.employee.id = :employeeId and e.status = 'APPROVED' " +
            "and e.payrollItemId is null and e.decidedAt < :before and e.deleted = false order by e.decidedAt asc")
    List<LeaveEncashment> findApprovedUnpaid(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                                             @Param("before") LocalDateTime before);
}
