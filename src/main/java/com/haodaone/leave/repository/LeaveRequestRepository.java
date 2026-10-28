package com.haodaone.leave.repository;

import com.haodaone.leave.entity.LeaveRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    @Override
    java.util.Optional<LeaveRequest> findById(Long id);

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    java.util.Optional<LeaveRequest> findByIdAndCompany_Id(Long id, Long companyId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    List<LeaveRequest> findAllByCompany_IdOrderByStartDateDesc(Long companyId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    List<LeaveRequest> findAllByCompany_IdAndStatusOrderByStartDateAsc(Long companyId, String status);

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    List<LeaveRequest> findAllByEmployeeIdOrderByStartDateDesc(Long employeeId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    List<LeaveRequest> findAllByEmployee_Company_IdAndEmployeeIdOrderByStartDateDesc(Long companyId, Long employeeId);

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    List<LeaveRequest> findAllByStatusOrderByStartDateAsc(String status);

    /** Same as findAllByStatusOrderByStartDateAsc but scoped to a specific
     *  set of employees - powers a manager's "my team" approval queue so
     *  they see their direct reports' requests, not the whole company's. */
    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    List<LeaveRequest> findAllByEmployeeIdInAndStatusOrderByStartDateAsc(List<Long> employeeIds, String status);

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    List<LeaveRequest> findAllByCompany_IdAndEmployeeIdInAndStatusOrderByStartDateAsc(Long companyId, List<Long> employeeIds, String status);

    /** Team-scoped equivalent of findAllByOrderByStartDateDesc - every status, not just one. */
    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    List<LeaveRequest> findAllByEmployeeIdInOrderByStartDateDesc(List<Long> employeeIds);

    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.designation", "leaveType", "decidedBy"})
    List<LeaveRequest> findAllByCompany_IdAndEmployeeIdInOrderByStartDateDesc(Long companyId, List<Long> employeeIds);

    List<LeaveRequest> findAllByOrderByStartDateDesc();

        @Query("select lr from LeaveRequest lr where lr.employee.company.id = :companyId and lr.status = 'APPROVED' and lr.startDate <= :today and lr.endDate >= :today")
        List<LeaveRequest> findActiveOnForCompany(@Param("companyId") Long companyId, @Param("today") LocalDate today);

    /** Approved days for one employee/leaveType/year - the "used" half of the balance calculation (see LeaveBalance javadoc). */
    @Query("select coalesce(sum(lr.days), 0) from LeaveRequest lr where lr.employee.id = :employeeId " +
            "and lr.company.id = :companyId and lr.leaveType.id = :leaveTypeId and lr.status = 'APPROVED' and year(lr.startDate) = :year")
    double sumApprovedDays(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("leaveTypeId") Long leaveTypeId, @Param("year") int year);

    /** Approved days of one type whose start date falls in [from, to] - used for carry-forward expiry (V45). */
    @Query("select coalesce(sum(lr.days), 0) from LeaveRequest lr where lr.employee.id = :employeeId " +
            "and lr.company.id = :companyId and lr.leaveType.id = :leaveTypeId and lr.status = 'APPROVED' and lr.startDate between :from and :to")
    double sumApprovedDaysBetween(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("leaveTypeId") Long leaveTypeId,
                                  @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Every approved request of one type for one employee, oldest first - leave ledger and comp-off FIFO (V45). */
    @Query("select lr from LeaveRequest lr where lr.employee.id = :employeeId and lr.company.id = :companyId " +
            "and lr.leaveType.id = :leaveTypeId and lr.status = 'APPROVED' order by lr.startDate asc")
    List<LeaveRequest> findApprovedForEmployeeAndType(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId,
                                                      @Param("leaveTypeId") Long leaveTypeId);

    /** Year-independent pending days - comp-off credits are not bound to a leave year (V45). */
    @Query("select coalesce(sum(lr.days), 0) from LeaveRequest lr where lr.employee.id = :employeeId " +
            "and lr.company.id = :companyId and lr.leaveType.id = :leaveTypeId and lr.status = 'PENDING'")
    double sumPendingDaysAllYears(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("leaveTypeId") Long leaveTypeId);

    /** Days still awaiting a decision - counted as committed so several pending requests can't overspend a balance. */
    @Query("select coalesce(sum(lr.days), 0) from LeaveRequest lr where lr.employee.id = :employeeId " +
            "and lr.company.id = :companyId and lr.leaveType.id = :leaveTypeId and lr.status = 'PENDING' and year(lr.startDate) = :year")
    double sumPendingDays(@Param("companyId") Long companyId, @Param("employeeId") Long employeeId, @Param("leaveTypeId") Long leaveTypeId, @Param("year") int year);

    /** Any overlapping APPROVED or PENDING request for this employee - used to block double-booking the same dates. */
    @Query("select lr from LeaveRequest lr where lr.employee.id = :employeeId and lr.status in ('PENDING','APPROVED') " +
            "and lr.startDate <= :endDate and lr.endDate >= :startDate")
        List<LeaveRequest> findOverlapping(@Param("employeeId") Long employeeId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /** Employees currently on approved leave covering today - powers the Dashboard "On Leave" widget and team calendar. */
    @Query("select lr from LeaveRequest lr where lr.status = 'APPROVED' and lr.startDate <= :today and lr.endDate >= :today")
    List<LeaveRequest> findActiveOn(@Param("today") LocalDate today);

    /** Approved leave in this company touching [start, end] - feeds the monthly attendance muster. */
    @Query("select lr from LeaveRequest lr join fetch lr.leaveType join fetch lr.employee where lr.company.id = :companyId " +
            "and lr.status = 'APPROVED' and lr.startDate <= :end and lr.endDate >= :start")
    List<LeaveRequest> findApprovedOverlappingForCompany(@Param("companyId") Long companyId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    long countByCompany_IdAndStatusAndStartDateBetween(Long companyId, String status, LocalDate start, LocalDate end);

    long countByCompany_IdAndEmployee_IdInAndStatusAndStartDateBetween(Long companyId, Set<Long> employeeIds, String status, LocalDate start, LocalDate end);

    @Query("select lr.leaveType.name, coalesce(sum(lr.days), 0) from LeaveRequest lr where lr.company.id = :companyId and lr.employee.id in :employeeIds and lr.status = 'APPROVED' and year(lr.startDate) = :year group by lr.leaveType.name")
    List<Object[]> sumApprovedDaysByLeaveTypeScoped(@Param("companyId") Long companyId, @Param("employeeIds") Set<Long> employeeIds, @Param("year") int year);

    @Query("select lr.employee.department.name, coalesce(sum(lr.days), 0) from LeaveRequest lr where lr.company.id = :companyId and lr.employee.id in :employeeIds and lr.status = 'APPROVED' and year(lr.startDate) = :year and lr.employee.department is not null group by lr.employee.department.name")
    List<Object[]> sumApprovedDaysByDepartmentScoped(@Param("companyId") Long companyId, @Param("employeeIds") Set<Long> employeeIds, @Param("year") int year);

    @Query("select lr.leaveType.name, coalesce(sum(lr.days), 0) from LeaveRequest lr " +
            "where lr.company.id = :companyId and lr.status = 'APPROVED' and year(lr.startDate) = :year group by lr.leaveType.name")
    List<Object[]> sumApprovedDaysByLeaveTypeForCompany(@Param("companyId") Long companyId, @Param("year") int year);

    @Query("select lr.employee.department.name, coalesce(sum(lr.days), 0) from LeaveRequest lr " +
            "where lr.company.id = :companyId and lr.status = 'APPROVED' and year(lr.startDate) = :year and lr.employee.department is not null " +
            "group by lr.employee.department.name")
    List<Object[]> sumApprovedDaysByDepartmentForCompany(@Param("companyId") Long companyId, @Param("year") int year);
}
