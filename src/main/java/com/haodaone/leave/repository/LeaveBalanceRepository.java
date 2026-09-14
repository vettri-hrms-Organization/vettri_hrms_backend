package com.haodaone.leave.repository;

import com.haodaone.leave.entity.LeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {
    List<LeaveBalance> findAllByEmployeeIdAndYear(Long employeeId, int year);
    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(Long employeeId, Long leaveTypeId, int year);
    Optional<LeaveBalance> findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(Long companyId, Long employeeId, Long leaveTypeId, int year);
}
