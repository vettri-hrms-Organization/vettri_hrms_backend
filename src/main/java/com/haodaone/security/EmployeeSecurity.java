package com.haodaone.security;

import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Backs the "or is this their own record" half of @PreAuthorize checks
 * that let a plain EMPLOYEE (seeded with zero permissions - see
 * DataSeeder) view and manage their own data without granting them a
 * permission broad enough to see everyone else's too.
 *
 * Referenced from controllers as @employeeSecurity.isSelf(#employeeId) -
 * the bean name (first letter lowercased) is how Spring's @PreAuthorize
 * SpEL resolves it.
 *
 * Deliberately resolves "self" via the User -> Employee link
 * (EmployeeRepository.findByUser_Username...) rather than trusting a
 * client-supplied employeeId matching some session value, so there's a
 * single source of truth for "who is this login" that can't be spoofed
 * by the request.
 */
@Component("employeeSecurity")
public class EmployeeSecurity {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public EmployeeSecurity(EmployeeRepository employeeRepository, LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    /** True if the given employeeId belongs to the currently authenticated login. */
    public boolean isSelf(Long employeeId) {
        if (employeeId == null) {
            return false;
        }
        return currentEmployeeId().map(employeeId::equals).orElse(false);
    }

    /** True if the given leave request was filed by the currently authenticated login. */
    public boolean ownsLeaveRequest(Long leaveRequestId) {
        if (leaveRequestId == null) {
            return false;
        }
        return currentEmployeeId()
                .flatMap(myId -> leaveRequestRepository.findById(leaveRequestId)
                        .map(lr -> lr.getEmployee() != null && myId.equals(lr.getEmployee().getId())))
                .orElse(false);
    }

    private java.util.Optional<Long> currentEmployeeId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return employeeRepository.findByUser_UsernameAndDeletedFalse(username).map(e -> e.getId());
    }
}
