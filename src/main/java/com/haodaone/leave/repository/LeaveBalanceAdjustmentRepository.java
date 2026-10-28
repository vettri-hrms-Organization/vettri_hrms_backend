package com.haodaone.leave.repository;

import com.haodaone.leave.entity.LeaveBalanceAdjustment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LeaveBalanceAdjustmentRepository extends JpaRepository<LeaveBalanceAdjustment, Long> {

    @EntityGraph(attributePaths = {"leaveType", "adjustedBy"})
    List<LeaveBalanceAdjustment> findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByEffectiveDateAscIdAsc(Long companyId, Long employeeId);

    @Query("select coalesce(sum(a.days), 0) from LeaveBalanceAdjustment a where a.company.id = :companyId and a.employee.id = :employeeId " +
            "and a.leaveType.id = :leaveTypeId and a.leaveYear = :year and a.deleted = false")
    double sumDays(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                   @Param("leaveTypeId") Long leaveTypeId, @Param("year") int year);

    @Query("select coalesce(sum(a.days), 0) from LeaveBalanceAdjustment a where a.company.id = :companyId and a.employee.id = :employeeId " +
            "and a.leaveType.id = :leaveTypeId and a.deleted = false")
    double sumDaysAllYears(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                           @Param("leaveTypeId") Long leaveTypeId);
}
