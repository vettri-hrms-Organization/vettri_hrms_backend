package com.haodaone.customfield.controller;

import com.haodaone.customfield.dto.CustomFieldDTO;
import com.haodaone.customfield.service.CustomFieldService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.CompanySecurity;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Employee custom fields.
 * Definitions: anyone signed in can read (employees only see fields visible to them);
 * create/update/delete/reorder need EMPLOYEE_MANAGE with organisation scope.
 * Values: HR within their EMPLOYEE_VIEW / EMPLOYEE_MANAGE scope, or the employee themself
 * (visible fields; edits limited to fields marked editable by employee).
 */
@RestController
@RequestMapping("/api/custom-fields")
public class CustomFieldController {

    private static final String MANAGE_DEFINITIONS = "@authorizationService.hasOrganizationScope('EMPLOYEE_MANAGE')";

    private final CustomFieldService service;
    private final AuthorizationService authorizationService;
    private final CompanySecurity companySecurity;

    public CustomFieldController(CustomFieldService service, AuthorizationService authorizationService,
                                 CompanySecurity companySecurity) {
        this.service = service;
        this.authorizationService = authorizationService;
        this.companySecurity = companySecurity;
    }

    @GetMapping("/definitions")
    @PreAuthorize("isAuthenticated()")
    public List<CustomFieldDTO.Definition> definitions() {
        boolean hrView = hasAuthority("EMPLOYEE_VIEW") || hasAuthority("EMPLOYEE_CREATE")
                || authorizationService.hasOrganizationScope("EMPLOYEE_MANAGE");
        return service.listDefinitions(!hrView);
    }

    @PostMapping("/definitions")
    @PreAuthorize(MANAGE_DEFINITIONS)
    public ResponseEntity<CustomFieldDTO.Definition> create(@Valid @RequestBody CustomFieldDTO.DefinitionRequest request) {
        return ResponseEntity.status(201).body(service.createDefinition(request));
    }

    @PutMapping("/definitions/{id}")
    @PreAuthorize(MANAGE_DEFINITIONS)
    public CustomFieldDTO.Definition update(@PathVariable Long id, @Valid @RequestBody CustomFieldDTO.DefinitionRequest request) {
        return service.updateDefinition(id, request);
    }

    @DeleteMapping("/definitions/{id}")
    @PreAuthorize(MANAGE_DEFINITIONS)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteDefinition(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/definitions/order")
    @PreAuthorize(MANAGE_DEFINITIONS)
    public List<CustomFieldDTO.Definition> reorder(@Valid @RequestBody CustomFieldDTO.ReorderRequest request) {
        return service.reorder(request.ids());
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('EMPLOYEE_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public CustomFieldDTO.EmployeeValues employeeValues(@PathVariable Long employeeId) {
        boolean hrView = authorizationService.isAllowed("EMPLOYEE_VIEW", "EMPLOYEE", employeeId);
        boolean canManage = canManage(employeeId);
        return service.getValues(employeeId, !hrView && !canManage, canManage);
    }

    @PutMapping("/employees/{employeeId}")
    @PreAuthorize("(hasAuthority('EMPLOYEE_MANAGE') and (@companySecurity.canManageEmployee(#employeeId) or @companySecurity.isSuperAdmin())) or @employeeSecurity.isSelf(#employeeId)")
    public CustomFieldDTO.EmployeeValues saveEmployeeValues(@PathVariable Long employeeId,
                                                            @Valid @RequestBody CustomFieldDTO.SaveValuesRequest request) {
        return service.saveValues(employeeId, request.values(), !canManage(employeeId));
    }

    /** Pre-flight check used by the create-employee form before the employee exists. */
    @PostMapping("/validate")
    @PreAuthorize("hasAuthority('EMPLOYEE_CREATE') or hasAuthority('EMPLOYEE_MANAGE')")
    public CustomFieldDTO.ValidationResponse validate(@Valid @RequestBody CustomFieldDTO.SaveValuesRequest request) {
        return service.validateValues(request.values());
    }

    /** Display values for the employee CSV export, limited to the caller's EMPLOYEE_VIEW scope. */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW')")
    public CustomFieldDTO.ExportResponse export() {
        Optional<Set<Long>> scope = authorizationService.resolveEmployeeIds("EMPLOYEE_VIEW");
        return service.exportValues(scope.orElse(null));
    }

    private boolean canManage(Long employeeId) {
        return hasAuthority("EMPLOYEE_MANAGE")
                && (companySecurity.canManageEmployee(employeeId) || companySecurity.isSuperAdmin());
    }

    private static boolean hasAuthority(String code) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> code.equals(authority.getAuthority()));
    }
}
