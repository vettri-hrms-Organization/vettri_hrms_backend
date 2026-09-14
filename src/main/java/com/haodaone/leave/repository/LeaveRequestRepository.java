package com.haodaone.leave.repository;

import com.haodaone.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    List<LeaveRequest> findAllByCompany_IdOrderByStartDateDesc(Long companyId);
    List<LeaveRequest> findAllByCompany_IdAndStatusOrderByStartDateAsc(Long companyId, String status);

    List<LeaveRequest> findAllByEmployeeIdOrderByStartDateDesc(Long employeeId);

        List<LeaveRequest> findAllByEmployee_Company_IdAndEmployeeIdOrderByStartDateDesc(Long companyId, Long employeeId);

    List<LeaveRequest> findAllByStatusOrderByStartDateAsc(String status);

    /** Same as findAllByStatusOrderByStartDateAsc but scoped to a specific
     *  set of employees - powers a manager's "my team" approval queue so
     *  they see their direct reports' requests, not the whole company's. */
    List<LeaveRequest> findAllByEmployeeIdInAndStatusOrderByStartDateAsc(List<Long> employeeIds, String status);

    /** Team-scoped equivalent of findAllByOrderByStartDateDesc - every status, not just one. */
    List<LeaveRequest> findAllByEmployeeIdInOrderByStartDateDesc(List<Long> employeeIds);

    List<LeaveRequest> findAllByOrderByStartDateDesc();

        @Query("select lr from LeaveRequest lr where lr.employee.company.id = :companyId and lr.status = 'APPROVED' and lr.startDate <= :today and lr.endDate >= :today")
        List<LeaveRequest> findActiveOnForCompany(@Param("companyId") Long companyId, @Param("today") LocalDate today);

    /** Approved days for one employee/leaveType/year - the "used" half of the balance calculation (see LeaveBalance javadoc). */
    @Query("select coalesce(sum(lr.days), 0) from LeaveRequest lr where lr.employee.id = :employeeId " +
            "and lr.company.id = :companyId and lr.leaveType.id = :leaveTypeId and lr.status = 'APPROVED' and year(lr.startDate) = :year")
    double sumApprovedDays(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("leaveTypeId") Long leaveTypeId, @Param("year") int year);

    /** Any overlapping APPROVED or PENDING request for this employee - used to block double-booking the same dates. */
    @Query("select lr from LeaveRequest lr where lr.employee.id = :employeeId and lr.status in ('PENDING','APPROVED') " +
            "and lr.startDate <= :endDate and lr.endDate >= :startDate")
        List<LeaveRequest> findOverlapping(@Param("employeeId") Long employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** Employees currently on approved leave covering today - powers the Dashboard "On Leave" widget and team calendar. */
    @Query("select lr from LeaveRequest lr where lr.status = 'APPROVED' and lr.startDate <= :today and lr.endDate >= :today")
    List<LeaveRequest> findActiveOn(@Param("today") LocalDate today);

    long countByStatusAndStartDateBetween(String status, LocalDate start, LocalDate end);

    @Query("select lr.leaveType.name, coalesce(sum(lr.days), 0) from LeaveRequest lr " +
            "where lr.status = 'APPROVED' and year(lr.startDate) = :year group by lr.leaveType.name")
    List<Object[]> sumApprovedDaysByLeaveType(@Param("year") int year);

    @Query("select lr.employee.department.name, coalesce(sum(lr.days), 0) from LeaveRequest lr " +
            "where lr.status = 'APPROVED' and year(lr.startDate) = :year and lr.employee.department is not null " +
            "group by lr.employee.department.name")
    List<Object[]> sumApprovedDaysByDepartment(@Param("year") int year);
}
