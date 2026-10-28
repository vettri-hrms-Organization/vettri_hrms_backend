package com.haodaone.imports.repository;

import com.haodaone.imports.entity.OpeningLeaveBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OpeningLeaveBalanceRepository extends JpaRepository<OpeningLeaveBalance, Long> {

    Optional<OpeningLeaveBalance> findFirstByCompany_IdAndEmployee_IdAndLeaveType_IdAndLeaveYearAndDeletedFalse(
            Long companyId, Long employeeId, Long leaveTypeId, int leaveYear);
}
