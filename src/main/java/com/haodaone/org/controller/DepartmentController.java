package com.haodaone.org.controller;

import com.haodaone.org.dto.DepartmentDTO;
import com.haodaone.org.service.DepartmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORG_VIEW')")
    public List<DepartmentDTO> listAll() {
        return departmentService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ResponseEntity<DepartmentDTO> create(@Valid @RequestBody DepartmentDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(departmentService.create(request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        departmentService.setActive(id, true);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        departmentService.setActive(id, false);
        return ResponseEntity.noContent().build();
    }
}
