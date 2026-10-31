package com.haodaone.performance.suite;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** Shared helpers for the performance suite: who is calling, whether they're HR, people lookups and notifications. */
@Component
public class PerfContext {

    private final EmployeeRepository employees;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;

    public PerfContext(EmployeeRepository employees, AuthorizationService authorizationService, NotificationService notificationService) {
        this.employees = employees;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
    }

    public Long companyId() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }

    /** The caller's employee record (including people serving notice). */
    public Employee me() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserPrincipal principal)) throw new BadRequestException("Sign in again");
        Long companyId = companyId();
        return employees.findByUser_IdAndDeletedFalse(principal.getId())
                .filter(e -> e.getCompany() != null && e.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new BadRequestException("Your login isn't linked to an employee record"));
    }

    public Employee meOrNull() {
        try {
            return me();
        } catch (BadRequestException ex) {
            return null;
        }
    }

    /** HR / performance admins for the whole organization. */
    public boolean isHr() {
        return authorizationService.hasOrganizationScope("PERFORMANCE_MANAGE");
    }

    public void requireHr() {
        if (!isHr()) throw new AccessDeniedException("Only HR can do this");
    }

    public boolean hasOrgScope(String permission) {
        return authorizationService.hasOrganizationScope(permission);
    }

    /** True when {@code managerId} is the employee's reporting manager. */
    public boolean manages(Long managerId, Employee employee) {
        return managerId != null && employee.getReportingManager() != null && managerId.equals(employee.getReportingManager().getId());
    }

    public Employee employee(Long id) {
        return employees.findByIdAndCompany_IdAndDeletedFalse(id, companyId()).orElseThrow(() -> new BadRequestException("Employee not found"));
    }

    public Map<Long, Employee> people(Collection<Long> ids) {
        Set<Long> clean = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (clean.isEmpty()) return Map.of();
        return employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId(), clean).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
    }

    public List<Employee> activeEmployees() {
        return employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId()).stream().filter(e -> {
            String s = com.haodaone.employee.entity.EmploymentStatus.normalize(e.getStatus());
            return !com.haodaone.employee.entity.EmploymentStatus.RESIGNED.equals(s) && !com.haodaone.employee.entity.EmploymentStatus.TERMINATED.equals(s);
        }).toList();
    }

    public record Person(Long id, String name, String code, String designation, Long departmentId, String department, Long managerId) { }
    public record Me(Long employeeId, boolean hr, boolean compAdmin, List<Long> reportIds) { }

    @Transactional(readOnly = true)
    public Me context() {
        Employee me = meOrNull();
        List<Long> reports = me == null ? List.of() : activeEmployees().stream().filter(e -> manages(me.getId(), e)).map(Employee::getId).toList();
        return new Me(me == null ? null : me.getId(), isHr(), hasOrgScope("SALARY_MANAGE"), reports);
    }

    @Transactional(readOnly = true)
    public List<Person> directory() {
        return activeEmployees().stream().map(e -> new Person(e.getId(), e.getFullName(), e.getEmployeeCode(),
                e.getDesignation() == null ? null : e.getDesignation().getTitle(),
                e.getDepartment() == null ? null : e.getDepartment().getId(), e.getDepartment() == null ? null : e.getDepartment().getName(),
                e.getReportingManager() == null ? null : e.getReportingManager().getId())).toList();
    }

    public void notify(Employee to, String type, String title, String message, String entityType, Long entityId) {
        if (to == null || to.getUser() == null) return;
        notificationService.notifyUser(companyId(), to.getUser().getId(), type, title, message, entityType, entityId, "NORMAL");
    }

    public static String name(Map<Long, Employee> people, Long id) {
        Employee e = id == null ? null : people.get(id);
        return e == null ? null : e.getFullName();
    }

    public static String text(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw new BadRequestException("Keep it under " + max + " characters");
        return v;
    }
}
