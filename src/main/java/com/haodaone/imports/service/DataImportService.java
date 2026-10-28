package com.haodaone.imports.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.imports.dto.ImportDTOs.BatchDTO;
import com.haodaone.imports.dto.ImportDTOs.ImportRowDTO;
import com.haodaone.imports.dto.ImportDTOs.ImportTypeDTO;
import com.haodaone.imports.dto.ImportDTOs.PreviewDTO;
import com.haodaone.imports.dto.ImportDTOs.ResultDTO;
import com.haodaone.imports.entity.DataImportBatch;
import com.haodaone.imports.repository.DataImportBatchRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Orchestrates every import in the hub: template download, preview (parse + validate, nothing written),
 * and commit. Commit re-validates the uploaded file, then either
 * <ul>
 *   <li>ALL_OR_NOTHING: refuses if any row has errors and applies every row in one transaction (any failure rolls
 *       everything back), or</li>
 *   <li>VALID_ONLY: applies each valid row in its own transaction, so one row failing at write time doesn't undo
 *       the others.</li>
 * </ul>
 * Every commit (including a failed all-or-nothing run) leaves a {@link DataImportBatch} row and an audit entry.
 * Each import needs ORGANIZATION scope on its permission (see {@link ImportType}).
 */
@Service
public class DataImportService {

    public static final String MODE_VALID_ONLY = "VALID_ONLY";
    public static final String MODE_ALL_OR_NOTHING = "ALL_OR_NOTHING";
    private static final int MAX_ERROR_REPORT = 60_000;

    private final Map<ImportType, ImportHandler> handlers = new EnumMap<>(ImportType.class);
    private final DataImportBatchRepository batchRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTemplate;

    public DataImportService(List<ImportHandler> handlers, DataImportBatchRepository batchRepository,
                             CompanyRepository companyRepository, EmployeeRepository employeeRepository,
                             AuthorizationService authorizationService, AuditLogService auditLogService,
                             PlatformTransactionManager transactionManager) {
        for (ImportHandler handler : handlers) this.handlers.put(handler.type(), handler);
        this.batchRepository = batchRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate.setReadOnly(true);
    }

    // ------------------------------------------------------------------ catalogue / template

    public List<ImportTypeDTO> types() {
        return Arrays.stream(ImportType.values())
                .filter(handlers::containsKey)
                .filter(this::canRun)
                .map(t -> ImportTypeDTO.of(t, handlers.get(t).columns()))
                .toList();
    }

    public byte[] template(String slug) {
        ImportType type = ImportType.fromSlug(slug);
        ImportHandler handler = handlerFor(type);
        return ImportSheet.template(type.title(), handler.columns(), handler.exampleRows(), handler.instructions());
    }

    public String templateFileName(String slug) {
        return ImportType.fromSlug(slug).slug() + "-import-template.xlsx";
    }

    // ------------------------------------------------------------------ preview / commit

    public PreviewDTO preview(String slug, MultipartFile file, ImportOptions options) {
        ImportType type = ImportType.fromSlug(slug);
        ImportHandler handler = handlerFor(type);
        Long companyId = requiredTenant();
        byte[] content = bytes(file);
        List<RowPlan> plans = validate(handler, companyId, content, file.getOriginalFilename(), options);
        return previewOf(type, handler, file.getOriginalFilename(), plans, sha256(content), companyId);
    }

    PreviewDTO previewOf(ImportType type, ImportHandler handler, String fileName, List<RowPlan> plans, String hash, Long companyId) {
        int invalid = (int) plans.stream().filter(p -> !p.valid()).count();
        int skipped = (int) plans.stream().filter(p -> p.valid() && p.skip()).count();
        int valid = plans.size() - invalid - skipped;
        BatchDTO previous = hash == null ? null : batchRepository
                .findFirstByCompany_IdAndImportTypeAndFileHashAndImportedRowsGreaterThanAndDeletedFalseOrderByCreatedAtDesc(
                        companyId, type.name(), hash, 0)
                .map(b -> BatchDTO.of(b, false)).orElse(null);
        StringBuilder message = new StringBuilder();
        message.append(valid).append(valid == 1 ? " row is" : " rows are").append(" ready to import");
        if (invalid > 0) message.append(", ").append(invalid).append(invalid == 1 ? " row needs" : " rows need").append(" attention");
        if (skipped > 0) message.append(", ").append(skipped).append(" will be skipped");
        message.append('.');
        if (previous != null) {
            message.append(" This exact file was already imported (batch #").append(previous.id()).append(").");
        }
        List<String> columns = handler.columns().stream().map(ImportSheet.Column::header).toList();
        return new PreviewDTO(type.slug(), type.title(), fileName, plans.size(), valid, invalid, skipped, columns,
                plans.stream().map(ImportRowDTO::of).toList(), message.toString(), previous);
    }

    public ResultDTO commit(String slug, MultipartFile file, boolean allOrNothing, ImportOptions options) {
        ImportType type = ImportType.fromSlug(slug);
        ImportHandler handler = handlerFor(type);
        Long companyId = requiredTenant();
        byte[] content = bytes(file);
        return commit(type, handler, companyId, file.getOriginalFilename(), content, allOrNothing, options);
    }

    ResultDTO commit(ImportType type, ImportHandler handler, Long companyId, String fileName, byte[] content,
                     boolean allOrNothing, ImportOptions options) {
        ImportOptions opts = options == null ? ImportOptions.DEFAULT : options;
        List<RowPlan> plans = validate(handler, companyId, content, fileName, opts);
        List<RowPlan> invalid = plans.stream().filter(p -> !p.valid()).toList();
        List<RowPlan> toApply = plans.stream().filter(p -> p.valid() && !p.skip()).toList();
        int skipped = (int) plans.stream().filter(p -> p.valid() && p.skip()).count();
        if (allOrNothing && !invalid.isEmpty()) {
            throw new BadRequestException(invalid.size() + (invalid.size() == 1 ? " row has" : " rows have")
                    + " errors. Fix them, or choose 'Import valid rows only'. Nothing was imported.");
        }
        if (toApply.isEmpty()) {
            throw new BadRequestException(skipped > 0
                    ? "Every valid row would be skipped - there is nothing new to import."
                    : "There are no valid rows to import.");
        }
        String hash = sha256(content);
        String importedBy = currentUserName();
        Map<Integer, String> failures = new LinkedHashMap<>();
        for (RowPlan plan : invalid) failures.put(plan.rowNumber(), String.join("; ", plan.errors()));

        DataImportBatch batch;
        int imported = 0;
        if (allOrNothing) {
            try {
                batch = transactionTemplate.execute(status -> {
                    DataImportBatch b = newBatch(type, companyId, fileName, hash, MODE_ALL_OR_NOTHING, opts, plans.size(), importedBy);
                    for (RowPlan plan : toApply) {
                        try {
                            handler.apply(companyId, plan, b, opts);
                        } catch (RuntimeException e) {
                            throw new RowFailure(plan.rowNumber(), messageOf(e), e);
                        }
                    }
                    finish(b, toApply.size(), skipped, 0, failures, type);
                    return b;
                });
            } catch (RowFailure failure) {
                failures.put(failure.row, failure.getMessage());
                transactionTemplate.executeWithoutResult(status -> {
                    DataImportBatch b = newBatch(type, companyId, fileName, hash, MODE_ALL_OR_NOTHING, opts, plans.size(), importedBy);
                    finish(b, 0, skipped, toApply.size(), failures, type);
                });
                throw new BadRequestException("Row " + failure.row + ": " + failure.getMessage()
                        + ". Nothing was imported (all-or-nothing).");
            }
            imported = toApply.size();
        } else {
            DataImportBatch created = transactionTemplate.execute(status ->
                    newBatch(type, companyId, fileName, hash, MODE_VALID_ONLY, opts, plans.size(), importedBy));
            for (RowPlan plan : toApply) {
                try {
                    transactionTemplate.executeWithoutResult(status -> handler.apply(companyId, plan, created, opts));
                    imported++;
                } catch (RuntimeException e) {
                    failures.put(plan.rowNumber(), messageOf(e));
                    plan.error(messageOf(e));
                }
            }
            int done = imported;
            int failedWrites = toApply.size() - imported;
            batch = transactionTemplate.execute(status -> {
                DataImportBatch b = batchRepository.findById(created.getId()).orElse(created);
                finish(b, done, skipped, failedWrites, failures, type);
                return b;
            });
        }
        int failed = plans.size() - imported - skipped;
        return new ResultDTO(batch.getId(), batch.getStatus(), plans.size(), imported, skipped, failed, batch.getSummary(),
                plans.stream().map(ImportRowDTO::of).toList());
    }

    private List<RowPlan> validate(ImportHandler handler, Long companyId, byte[] content, String fileName, ImportOptions options) {
        List<ImportSheet.SheetRow> rows = ImportSheet.read(content, fileName, handler.columns());
        ImportOptions opts = options == null ? ImportOptions.DEFAULT : options;
        // Read-only transaction so handlers can follow lazy associations while validating.
        List<RowPlan> plans = readOnlyTemplate.execute(status -> handler.validate(companyId, rows, opts));
        if (plans == null) plans = List.of();
        if (plans.size() != rows.size()) throw new IllegalStateException("Handler returned " + plans.size() + " plans for " + rows.size() + " rows");
        return plans;
    }

    private DataImportBatch newBatch(ImportType type, Long companyId, String fileName, String hash, String mode,
                                     ImportOptions options, int totalRows, String importedBy) {
        DataImportBatch batch = new DataImportBatch();
        batch.setCompany(companyRepository.getReferenceById(companyId));
        batch.setImportType(type.name());
        batch.setFileName(truncate(fileName, 255));
        batch.setFileHash(hash);
        batch.setMode(mode);
        batch.setOptions(truncate(options.describe(type), 255));
        batch.setTotalRows(totalRows);
        batch.setStatus(DataImportBatch.STATUS_FAILED);
        batch.setImportedByName(truncate(importedBy, 150));
        return batchRepository.save(batch);
    }

    private void finish(DataImportBatch batch, int imported, int skipped, int failedWrites, Map<Integer, String> failures, ImportType type) {
        int failed = batch.getTotalRows() - imported - skipped;
        batch.setImportedRows(imported);
        batch.setSkippedRows(skipped);
        batch.setFailedRows(Math.max(0, failed));
        batch.setStatus(imported == 0 ? DataImportBatch.STATUS_FAILED
                : failed > 0 ? DataImportBatch.STATUS_PARTIAL : DataImportBatch.STATUS_COMPLETED);
        String summary = type.title() + ": " + imported + " of " + batch.getTotalRows() + " row(s) imported"
                + (skipped > 0 ? ", " + skipped + " skipped" : "")
                + (failed > 0 ? ", " + failed + " not imported" : "")
                + (failedWrites > 0 && imported == 0 && MODE_ALL_OR_NOTHING.equals(batch.getMode()) ? " (rolled back)" : "")
                + ".";
        batch.setSummary(truncate(summary, 1000));
        if (!failures.isEmpty()) {
            StringBuilder report = new StringBuilder();
            for (Map.Entry<Integer, String> entry : failures.entrySet()) {
                String line = "Row " + entry.getKey() + ": " + entry.getValue() + "\n";
                if (report.length() + line.length() > MAX_ERROR_REPORT) {
                    report.append("...");
                    break;
                }
                report.append(line);
            }
            batch.setErrorReport(report.toString());
        }
        DataImportBatch saved = batchRepository.save(batch);
        auditLogService.log("DataImportBatch", saved.getId(), "IMPORT", summary
                + (saved.getFileName() != null ? " File: " + saved.getFileName() + "." : "")
                + (saved.getOptions() != null ? " Options: " + saved.getOptions() + "." : ""));
    }

    // ------------------------------------------------------------------ history

    public List<BatchDTO> batches(String slug, Integer limit) {
        Long companyId = requiredTenant();
        List<String> types = new ArrayList<>();
        if (slug != null && !slug.isBlank()) {
            ImportType type = ImportType.fromSlug(slug);
            if (!canRun(type)) throw new AccessDeniedException(type.permission() + " with organization scope is required.");
            types.add(type.name());
        } else {
            for (ImportType type : ImportType.values()) if (canRun(type)) types.add(type.name());
        }
        if (types.isEmpty()) return List.of();
        int size = limit == null ? 50 : Math.min(Math.max(limit, 1), 200);
        return batchRepository.findAllByCompany_IdAndImportTypeInAndDeletedFalseOrderByCreatedAtDescIdDesc(
                companyId, types, PageRequest.of(0, size)).stream().map(b -> BatchDTO.of(b, false)).toList();
    }

    public BatchDTO batch(Long id) {
        DataImportBatch batch = batchRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + id));
        ImportType type;
        try {
            type = ImportType.valueOf(batch.getImportType());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Import batch not found: " + id);
        }
        if (!canRun(type)) throw new AccessDeniedException(type.permission() + " with organization scope is required.");
        return BatchDTO.of(batch, true);
    }

    // ------------------------------------------------------------------ helpers

    private ImportHandler handlerFor(ImportType type) {
        if (!canRun(type)) throw new AccessDeniedException(type.permission() + " with organization scope is required for this import.");
        ImportHandler handler = handlers.get(type);
        if (handler == null) throw new ResourceNotFoundException("Import not available: " + type.slug());
        return handler;
    }

    private boolean canRun(ImportType type) {
        return authorizationService.hasOrganizationScope(type.permission());
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BadRequestException("Choose an .xlsx or .xls file to upload");
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
    }

    static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(new String(content, StandardCharsets.ISO_8859_1).hashCode());
        }
    }

    private String currentUserName() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) return null;
        return employeeRepository.findByUser_UsernameAndDeletedFalse(auth.getName())
                .map(e -> e.getFullName())
                .orElse(auth.getName());
    }

    private static String messageOf(RuntimeException e) {
        String message = e.getMessage();
        if (e instanceof AccessDeniedException) return "Not permitted: " + (message == null ? "access denied" : message);
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }

    /** Carries the failing row number out of the all-or-nothing transaction (which it rolls back). */
    private static final class RowFailure extends RuntimeException {
        private final int row;

        RowFailure(int row, String message, Throwable cause) {
            super(message, cause);
            this.row = row;
        }
    }
}
