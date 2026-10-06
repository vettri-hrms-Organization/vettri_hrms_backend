package com.haodaone.org.controller;

import com.haodaone.org.dto.OrganizationStructureDTO;
import com.haodaone.org.service.OrganizationStructureService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization")
public class OrganizationStructureController {

    private final OrganizationStructureService organizationStructureService;

    public OrganizationStructureController(OrganizationStructureService organizationStructureService) {
        this.organizationStructureService = organizationStructureService;
    }

    @GetMapping("/structure")
    @PreAuthorize("hasAuthority('ORG_VIEW')")
    public OrganizationStructureDTO getStructure() {
        return organizationStructureService.getStructure();
    }
}
