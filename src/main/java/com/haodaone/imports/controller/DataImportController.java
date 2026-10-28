package com.haodaone.imports.controller;

import com.haodaone.imports.dto.ImportDTOs.BatchDTO;
import com.haodaone.imports.dto.ImportDTOs.ImportTypeDTO;
import com.haodaone.imports.dto.ImportDTOs.OpeningYtdDTO;
import com.haodaone.imports.dto.ImportDTOs.PreviewDTO;
import com.haodaone.imports.dto.ImportDTOs.ResultDTO;
import com.haodaone.imports.service.DataImportService;
import com.haodaone.imports.service.ImportOptions;
import com.haodaone.imports.service.OpeningYtdService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Data import hub. Class-level gate: ORGANIZATION scope on at least one import permission; each import then
 * checks its own permission in {@link DataImportService} (salary: SALARY_MANAGE, leave balances and holidays:
 * LEAVE_MANAGE, bank details: EMPLOYEE_MANAGE).
 */
@RestController
@RequestMapping("/api/imports")
@PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE') or @authorizationService.hasOrganizationScope('LEAVE_MANAGE') or @authorizationService.hasOrganizationScope('EMPLOYEE_MANAGE')")
public class DataImportController {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final DataImportService importService;
    private final OpeningYtdService openingYtdService;

    public DataImportController(DataImportService importService, OpeningYtdService openingYtdService) {
        this.importService = importService;
        this.openingYtdService = openingYtdService;
    }

    @GetMapping("/types")
    public List<ImportTypeDTO> types() {
        return importService.types();
    }

    @GetMapping("/{type}/template")
    public ResponseEntity<byte[]> template(@PathVariable String type) {
        byte[] content = importService.template(type);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + importService.templateFileName(type) + "\"")
                .contentType(MediaType.parseMediaType(XLSX))
                .body(content);
    }

    @PostMapping(value = "/{type}/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreviewDTO preview(@PathVariable String type, @RequestPart("file") MultipartFile file,
                              @RequestParam(required = false) Boolean replace,
                              @RequestParam(required = false) String leaveMode) {
        return importService.preview(type, file, ImportOptions.of(replace, leaveMode));
    }

    /** allOrNothing=true: refuse if any row has errors and roll everything back on any failure; false: import valid rows only. */
    @PostMapping(value = "/{type}/commit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultDTO commit(@PathVariable String type, @RequestPart("file") MultipartFile file,
                            @RequestParam(defaultValue = "false") boolean allOrNothing,
                            @RequestParam(required = false) Boolean replace,
                            @RequestParam(required = false) String leaveMode) {
        return importService.commit(type, file, allOrNothing, ImportOptions.of(replace, leaveMode));
    }

    @GetMapping("/batches")
    public List<BatchDTO> batches(@RequestParam(required = false) String type, @RequestParam(required = false) Integer limit) {
        return importService.batches(type, limit);
    }

    @GetMapping("/batches/{id}")
    public BatchDTO batch(@PathVariable Long id) {
        return importService.batch(id);
    }

    @GetMapping("/opening-ytd")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public List<OpeningYtdDTO> openingYtd(@RequestParam(required = false) Integer fy) {
        return openingYtdService.list(fy);
    }

    @DeleteMapping("/opening-ytd/{id}")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public ResponseEntity<Void> deleteOpeningYtd(@PathVariable Long id) {
        openingYtdService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
