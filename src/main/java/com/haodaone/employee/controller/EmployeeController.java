package com.haodaone.employee.controller;

import com.haodaone.common.dto.PageResponse;
import com.haodaone.employee.dto.CreateEmployeeRequest;
import com.haodaone.employee.dto.EmployeeDetailDTO;
import com.haodaone.employee.dto.EmployeeSummaryDTO;
import com.haodaone.employee.dto.EmployeeImportResultDTO;
import com.haodaone.employee.dto.UpdateStatusRequest;
import com.haodaone.employee.service.EmployeeService;
import com.haodaone.employee.service.EmployeeImportService;
import com.haodaone.employee.service.EmployeeInvitationService;
import com.haodaone.employee.dto.InvitationResponse;
import com.haodaone.employee.dto.AccountStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeImportService employeeImportService;
    private final EmployeeInvitationService invitationService;

    public EmployeeController(EmployeeService employeeService, EmployeeImportService employeeImportService,
                               EmployeeInvitationService invitationService) {
        this.employeeService = employeeService;
        this.employeeImportService = employeeImportService;
        this.invitationService = invitationService;
    }

    /**
     * Unpaginated - kept exactly as it was. Several screens (reporting
     * manager pickers, interviewer pickers, the global search) call this
     * expecting a plain array and would break if the response shape
     * changed here. The Employee Directory page uses /paged below instead
     * of changing this one out from under its other callers.
     */
    @GetMapping
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_VIEW')")
    public List<EmployeeSummaryDTO> listAll(@RequestParam(required = false) String search) {
        return employeeService.listAll(search);
    }

    /** Paged directory listing - page is 0-indexed, size defaults to 25 and is capped at 100 (see EmployeeService#listPaged). departmentId optionally scopes it, e.g. the drill-down from Reports' department bars. */
    @GetMapping("/paged")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_VIEW')")
    public PageResponse<EmployeeSummaryDTO> listPaged(@RequestParam(required = false) String search,
                                                        @RequestParam(required = false) Long departmentId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size) {
        return employeeService.listPaged(search, departmentId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW') or @employeeSecurity.isSelf(#id)")
    public EmployeeDetailDTO getById(@PathVariable Long id) {
        return employeeService.getById(id);
    }

    @PostMapping
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_CREATE') and (@companySecurity.canCreateEmployee() or @companySecurity.isSuperAdmin())")
    public ResponseEntity<EmployeeDetailDTO> create(@Valid @RequestBody CreateEmployeeRequest request) {
        return ResponseEntity.status(201).body(employeeService.create(request));
    }

    @PostMapping("/{employeeId}/invitation")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_CREATE') and (@companySecurity.canCreateEmployee() or @companySecurity.isSuperAdmin())")
    public ResponseEntity<InvitationResponse> sendInvitation(@PathVariable Long employeeId) {
        return ResponseEntity.ok(invitationService.sendInvitation(employeeId));
    }

    @PostMapping("/{employeeId}/invitation/resend")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_CREATE') and (@companySecurity.canCreateEmployee() or @companySecurity.isSuperAdmin())")
    public ResponseEntity<InvitationResponse> resendInvitation(@PathVariable Long employeeId) {
        return ResponseEntity.ok(invitationService.resendInvitation(employeeId));
    }

    @PatchMapping("/{id}/account-status")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_MANAGE') and (@companySecurity.canManageEmployee(#id) or @companySecurity.isSuperAdmin())")
    public EmployeeDetailDTO setAccountStatus(@PathVariable Long id, @Valid @RequestBody AccountStatusRequest request) {
        return employeeService.setAccountStatus(id, request.getStatus());
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_CREATE') and (@companySecurity.canCreateEmployee() or @companySecurity.isSuperAdmin())")
    public EmployeeImportResultDTO previewImport(@RequestPart("file") MultipartFile file) {
        return employeeImportService.preview(file);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_CREATE') and (@companySecurity.canCreateEmployee() or @companySecurity.isSuperAdmin())")
    public EmployeeImportResultDTO importEmployees(@RequestPart("file") MultipartFile file,
                                                     @RequestParam(defaultValue = "false") boolean validOnly) {
        return employeeImportService.importEmployees(file, validOnly);
    }

    @GetMapping("/import/template")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_CREATE') and (@companySecurity.canCreateEmployee() or @companySecurity.isSuperAdmin())")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=employee-import-template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(employeeImportService.template());
    }

    @PutMapping("/{id}")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_MANAGE') and (@companySecurity.canManageEmployee(#id) or @companySecurity.isSuperAdmin())")
    public EmployeeDetailDTO update(@PathVariable Long id, @Valid @RequestBody CreateEmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_MANAGE') and (@companySecurity.canManageEmployee(#id) or @companySecurity.isSuperAdmin())")
    public EmployeeDetailDTO updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return employeeService.updateStatus(id, request.getStatus(), request.getReason());
    }

    @PatchMapping("/{id}/biometric-mapping")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('EMPLOYEE_MANAGE') and (@companySecurity.canManageEmployee(#id) or @companySecurity.isSuperAdmin())")
    public EmployeeDetailDTO setBiometricMapping(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        return employeeService.setBiometricMapping(id, body.get("deviceUserId"));
    }
}
