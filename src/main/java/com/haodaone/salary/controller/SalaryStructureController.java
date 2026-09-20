package com.haodaone.salary.controller;

import com.haodaone.salary.dto.SalaryStructureDTO;
import com.haodaone.salary.dto.UpsertSalaryStructureRequest;
import com.haodaone.salary.service.SalaryStructureService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Salary structure definition/revision.
 *
 * Authorization: view endpoints require SALARY_VIEW, the upsert endpoint
 * requires SALARY_MANAGE - see DataSeeder for how these map onto roles.
 */
@RestController
@RequestMapping("/api/salary/structures")
public class SalaryStructureController {

    private final SalaryStructureService salaryStructureService;

    public SalaryStructureController(SalaryStructureService salaryStructureService) {
        this.salaryStructureService = salaryStructureService;
    }

    @PreAuthorize("hasAuthority('SALARY_VIEW') or @employeeSecurity.isSelf(#employeeId)")
    @GetMapping("/employee/{employeeId}/current")
    public SalaryStructureDTO getCurrent(@PathVariable Long employeeId) {
        return salaryStructureService.getCurrent(employeeId);
    }

    @PreAuthorize("hasAuthority('SALARY_VIEW') or @employeeSecurity.isSelf(#employeeId)")
    @GetMapping("/employee/{employeeId}")
    public List<SalaryStructureDTO> getHistory(@PathVariable Long employeeId) {
        return salaryStructureService.getHistory(employeeId);
    }

    @PreAuthorize("hasAuthority('SALARY_MANAGE')")
    @PostMapping
    public ResponseEntity<SalaryStructureDTO> upsert(@Valid @RequestBody UpsertSalaryStructureRequest request) {
        return ResponseEntity.status(201).body(salaryStructureService.upsert(request));
    }
}
