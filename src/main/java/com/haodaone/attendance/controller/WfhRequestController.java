package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.WfhRequestDTO;
import com.haodaone.attendance.entity.WfhRequest;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/attendance")
public class WfhRequestController {

    private final WfhRequestRepository wfhRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock applicationClock;
    private final com.haodaone.security.AuthorizationService authorizationService;

    public WfhRequestController(WfhRequestRepository wfhRequestRepository, EmployeeRepository employeeRepository, Clock applicationClock,
                                com.haodaone.security.AuthorizationService authorizationService) {
        this.wfhRequestRepository = wfhRequestRepository;
        this.employeeRepository = employeeRepository;
        this.applicationClock = applicationClock;
        this.authorizationService = authorizationService;
    }

    @PostMapping("/wfh/request")
    @PreAuthorize("@employeeSecurity.isLinkedEmployee() or hasAuthority('EMPLOYEE_VIEW')")
    @Transactional
    public ResponseEntity<WfhRequestDTO> requestWfh(@RequestBody @Valid WfhRequestDTO request) {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);
        LocalDate workDate = request.getWorkDate();
        if (workDate == null) {
            throw new BadRequestException("WORK_DATE_REQUIRED");
        }
        if (wfhRequestRepository.findByEmployee_IdAndCompany_IdAndWorkDateAndDeletedFalse(employee.getId(), company.getId(), workDate).isPresent()) {
            throw new BadRequestException("WFH_REQUEST_ALREADY_EXISTS");
        }

        WfhRequest entity = new WfhRequest();
        entity.setCompany(company);
        entity.setEmployee(employee);
        entity.setWorkDate(workDate);
        entity.setReason(request.getReason());
        entity.setStatus("PENDING");
        entity = wfhRequestRepository.save(entity);

        return ResponseEntity.status(201).body(toDto(entity));
    }

    @GetMapping("/wfh/my")
    @PreAuthorize("@employeeSecurity.isLinkedEmployee() or hasAuthority('EMPLOYEE_VIEW')")
    @Transactional(readOnly = true)
    public List<WfhRequestDTO> myWfhRequests() {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);
        return wfhRequestRepository.findAllByEmployee_IdAndCompany_IdAndDeletedFalseOrderByWorkDateDesc(employee.getId(), company.getId())
                .stream().map(this::toDto).toList();
    }

    @GetMapping("/wfh/team")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE') or hasAuthority('ATTENDANCE_VIEW')")
    public List<WfhRequestDTO> teamWfhRequests() {
        Long companyId = requireCompany(currentEmployee()).getId();
        var scope = authorizationService.resolveEmployeeIds("LEAVE_APPROVE");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        var rows = scope.isEmpty()
            ? wfhRequestRepository.findAllByCompany_IdAndStatusAndDeletedFalseOrderByWorkDateDesc(companyId, "PENDING")
            : wfhRequestRepository.findAllByCompany_IdAndEmployee_IdInAndStatusAndDeletedFalseOrderByWorkDateDesc(companyId, scope.get(), "PENDING");
        return rows
                .stream().map(this::toDto).toList();
    }

    @PatchMapping("/wfh/{id}/approve")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE') and @authorizationService.isAllowedWfhRequest(#id)")
    @Transactional
    public ResponseEntity<WfhRequestDTO> approve(@PathVariable Long id, @RequestParam(required = false) String note) {
        WfhRequest entity = wfhRequestRepository.findById(id).orElseThrow(() -> new BadRequestException("WFH_REQUEST_NOT_FOUND"));
        Employee manager = currentEmployee();
        if (!Objects.equals(entity.getCompany().getId(), manager.getCompany().getId())) {
            throw new BadRequestException("WFH_REQUEST_NOT_FOUND");
        }
        entity.setStatus("APPROVED");
        entity.setApprovedByEmployee(manager);
        entity.setApprovedAt(LocalDateTime.now(applicationClock));
        entity.setManagerNote(note);
        return ResponseEntity.ok(toDto(wfhRequestRepository.save(entity)));
    }

    @PatchMapping("/wfh/{id}/reject")
    @PreAuthorize("hasAuthority('LEAVE_APPROVE') and @authorizationService.isAllowedWfhRequest(#id)")
    @Transactional
    public ResponseEntity<WfhRequestDTO> reject(@PathVariable Long id, @RequestParam(required = false) String note) {
        WfhRequest entity = wfhRequestRepository.findById(id).orElseThrow(() -> new BadRequestException("WFH_REQUEST_NOT_FOUND"));
        Employee manager = currentEmployee();
        if (!Objects.equals(entity.getCompany().getId(), manager.getCompany().getId())) {
            throw new BadRequestException("WFH_REQUEST_NOT_FOUND");
        }
        entity.setStatus("REJECTED");
        entity.setApprovedByEmployee(manager);
        entity.setApprovedAt(LocalDateTime.now(applicationClock));
        entity.setManagerNote(note);
        return ResponseEntity.ok(toDto(wfhRequestRepository.save(entity)));
    }

    private WfhRequestDTO toDto(WfhRequest entity) {
        WfhRequestDTO dto = new WfhRequestDTO();
        dto.setId(entity.getId());
        dto.setEmployeeId(entity.getEmployee() != null ? entity.getEmployee().getId() : null);
        dto.setEmployeeName(entity.getEmployee() != null ? entity.getEmployee().getFullName() : null);
        dto.setWorkDate(entity.getWorkDate());
        dto.setReason(entity.getReason());
        dto.setStatus(entity.getStatus());
        dto.setManagerNote(entity.getManagerNote());
        return dto;
    }

    private Employee currentEmployee() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        if (username == null) {
            throw new BadRequestException("Authentication required");
        }

        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return employeeRepository.findByUser_UsernameAndDeletedFalse(username)
                .or(() -> employeeRepository.findByEmailIgnoreCaseAndDeletedFalse(username))
                .or(() -> principal instanceof com.haodaone.security.CustomUserPrincipal customUserPrincipal
                        ? employeeRepository.findByUser_IdAndDeletedFalse(customUserPrincipal.getId())
                                .or(() -> employeeRepository.findByEmailIgnoreCaseAndDeletedFalse(customUserPrincipal.getUser().getEmail()))
                        : java.util.Optional.empty())
                .orElseThrow(() -> new BadRequestException("Current login is not linked to an employee"));
    }

    private Company requireCompany(Employee employee) {
        if (employee.getCompany() == null) {
            throw new BadRequestException("Employee has no company assigned");
        }
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            tenant = employee.getCompany().getId();
            TenantContext.setCurrentTenant(tenant);
        }
        if (!Objects.equals(employee.getCompany().getId(), tenant)) {
            throw new BadRequestException("Company mismatch");
        }
        return employee.getCompany();
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
