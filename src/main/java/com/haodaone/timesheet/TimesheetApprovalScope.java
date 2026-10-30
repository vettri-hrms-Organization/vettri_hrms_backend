package com.haodaone.timesheet;

import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Whose timesheets the current user approves: the TIMESHEET_APPROVE scope (organization, department or team)
 * plus, as in Keka, their direct reports - so managers can approve even when the company hasn't set up teams.
 */
@Component
public class TimesheetApprovalScope {

    static final String APPROVE = "TIMESHEET_APPROVE";

    private final AuthorizationService authorizationService;
    private final EmployeeRepository employees;

    public TimesheetApprovalScope(AuthorizationService authorizationService, EmployeeRepository employees) {
        this.authorizationService = authorizationService;
        this.employees = employees;
    }

    /** Empty = whole company; otherwise the employee ids this user may approve. */
    public Optional<Set<Long>> employeeIds() {
        if (!hasApprovePermission()) return Optional.of(Set.of());
        Optional<Set<Long>> scoped = authorizationService.resolveEmployeeIds(APPROVE);
        if (scoped.isEmpty()) return scoped;
        Set<Long> ids = new HashSet<>(scoped.get());
        ids.addAll(directReports());
        return Optional.of(ids);
    }

    public boolean canApprove(Long employeeId) {
        if (employeeId == null || !hasApprovePermission()) return false;
        return authorizationService.isAllowed(APPROVE, "EMPLOYEE", employeeId) || directReports().contains(employeeId);
    }

    private Set<Long> directReports() {
        Employee me = authorizationService.resolveCurrentEmployee();
        Long companyId = TenantContext.getCurrentTenant();
        if (me == null || companyId == null) return Set.of();
        return new HashSet<>(employees.findIdsByCompanyAndReportingManager(companyId, me.getId()));
    }

    private static boolean hasApprovePermission() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(APPROVE::equals);
    }
}
