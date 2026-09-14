package com.haodaone.selfservice.controller;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@PreAuthorize("isAuthenticated()")
public class EmployeeSelfServiceController {
    private final JdbcTemplate jdbcTemplate;
    private final EmployeeRepository employeeRepository;

    public EmployeeSelfServiceController(JdbcTemplate jdbcTemplate, EmployeeRepository employeeRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.employeeRepository = employeeRepository;
    }

    @GetMapping("/employee-assets/me")
    @PreAuthorize("hasRole('EMPLOYEE') or hasAuthority('EMPLOYEE_VIEW')")
    public List<Map<String, Object>> myAssets() {
        Employee employee = currentEmployee();
        return jdbcTemplate.queryForList("""
                select id, asset_type, asset_tag, description, status, assigned_at, returned_at, notes
                from employee_asset
                where company_id = ? and employee_id = ? and deleted = false
                order by assigned_at desc nulls last, id desc
                """, employee.getCompany().getId(), employee.getId());
    }

    @GetMapping("/notifications")
    public List<Map<String, Object>> notifications() {
        Employee employee = currentEmployee();
        return jdbcTemplate.queryForList("""
                select id, type, title, message, entity_type, entity_id, priority, created_at, read_at
                from notification
                where company_id = ? and recipient_user_id = ? and deleted = false
                order by created_at desc
                """, employee.getCompany().getId(), employee.getUser().getId());
    }

    @PatchMapping("/notifications/{id}/read")
    public void markNotificationRead(@PathVariable Long id) {
        Employee employee = currentEmployee();
        int updated = jdbcTemplate.update("""
                update notification set read_at = coalesce(read_at, ?), updated_at = ?
                where id = ? and company_id = ? and recipient_user_id = ? and deleted = false
                """, Timestamp.valueOf(LocalDateTime.now()), Timestamp.valueOf(LocalDateTime.now()), id,
                employee.getCompany().getId(), employee.getUser().getId());
        if (updated == 0) throw new BadRequestException("Notification not found");
    }

    @GetMapping("/support/requests")
    public List<Map<String, Object>> mySupportRequests() {
        Employee employee = currentEmployee();
        return jdbcTemplate.queryForList("""
                select id, category, subject, description, priority, status, resolution_note, created_at, closed_at
                from support_request
                where company_id = ? and requester_employee_id = ? and deleted = false
                order by created_at desc
                """, employee.getCompany().getId(), employee.getId());
    }

    @PostMapping("/support/requests")
    public Map<String, Object> createSupportRequest(@RequestBody Map<String, String> request) {
        Employee employee = currentEmployee();
        String subject = required(request, "subject");
        String description = required(request, "description");
        String category = request.getOrDefault("category", "GENERAL");
        String priority = request.getOrDefault("priority", "NORMAL");
        Long id = jdbcTemplate.queryForObject("""
                insert into support_request(company_id, requester_employee_id, category, subject, description, priority)
                values (?, ?, ?, ?, ?, ?) returning id
                """, Long.class, employee.getCompany().getId(), employee.getId(), category, subject, description, priority);
        return Map.of("id", id, "status", "OPEN", "subject", subject);
    }

    private Employee currentEmployee() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Employee employee = employeeRepository.findByUser_UsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BadRequestException("Current login is not linked to an employee"));
        if (employee.getCompany() == null || employee.getUser() == null) {
            throw new BadRequestException("Employee account is missing workspace information");
        }
        return employee;
    }

    private String required(Map<String, String> request, String key) {
        String value = request.get(key);
        if (value == null || value.isBlank()) throw new BadRequestException(key + " is required");
        return value.trim();
    }
}
