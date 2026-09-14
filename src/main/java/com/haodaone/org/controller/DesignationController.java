package com.haodaone.org.controller;

import com.haodaone.org.dto.DesignationDTO;
import com.haodaone.org.service.DesignationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/designations")
public class DesignationController {

    private final DesignationService designationService;

    public DesignationController(DesignationService designationService) {
        this.designationService = designationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORG_VIEW')")
    public List<DesignationDTO> listAll() {
        return designationService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORG_MANAGE')")
    public ResponseEntity<DesignationDTO> create(@Valid @RequestBody DesignationDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(designationService.create(request));
    }
}
