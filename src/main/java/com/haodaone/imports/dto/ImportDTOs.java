package com.haodaone.imports.dto;

import com.haodaone.imports.entity.DataImportBatch;
import com.haodaone.imports.entity.PayrollOpeningYtd;
import com.haodaone.imports.service.ImportSheet;
import com.haodaone.imports.service.ImportType;
import com.haodaone.imports.service.RowPlan;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Request/response shapes of /api/imports. */
public final class ImportDTOs {

    private ImportDTOs() {
    }

    public record ColumnDTO(String header, boolean required, String hint) {
        public static ColumnDTO of(ImportSheet.Column c) {
            return new ColumnDTO(c.header(), c.required(), c.hint());
        }
    }

    /** An import the current user may run. */
    public record ImportTypeDTO(String type, String title, String description, String permission, List<ColumnDTO> columns) {
        public static ImportTypeDTO of(ImportType type, List<ImportSheet.Column> columns) {
            return new ImportTypeDTO(type.slug(), type.title(), type.description(), type.permission(),
                    columns.stream().map(ColumnDTO::of).toList());
        }
    }

    /** One previewed row: values by column, what will happen, and why it can't be imported (errors) or needs a look (warnings). */
    public record ImportRowDTO(int row, Map<String, String> values, String action, boolean valid, boolean skip,
                               List<String> errors, List<String> warnings) {
        public static ImportRowDTO of(RowPlan plan) {
            return new ImportRowDTO(plan.rowNumber(), plan.values(), plan.action(), plan.valid(), plan.valid() && plan.skip(),
                    List.copyOf(plan.errors()), List.copyOf(plan.warnings()));
        }
    }

    public record PreviewDTO(String type, String title, String fileName, int totalRows, int validRows, int invalidRows,
                             int skippedRows, List<String> columns, List<ImportRowDTO> rows, String message,
                             BatchDTO previousImportOfSameFile) {
    }

    public record ResultDTO(Long batchId, String status, int totalRows, int importedRows, int skippedRows, int failedRows,
                            String message, List<ImportRowDTO> rows) {
    }

    public record BatchDTO(Long id, String type, String title, String fileName, String mode, String options, int totalRows,
                           int importedRows, int skippedRows, int failedRows, String status, String summary,
                           String importedBy, LocalDateTime createdAt, String errorReport) {
        public static BatchDTO of(DataImportBatch b, boolean withErrors) {
            String title;
            try {
                title = ImportType.valueOf(b.getImportType()).title();
            } catch (IllegalArgumentException e) {
                title = b.getImportType();
            }
            return new BatchDTO(b.getId(), b.getImportType(), title, b.getFileName(), b.getMode(), b.getOptions(),
                    b.getTotalRows(), b.getImportedRows(), b.getSkippedRows(), b.getFailedRows(), b.getStatus(),
                    b.getSummary(), b.getImportedByName() != null ? b.getImportedByName() : b.getCreatedBy(),
                    b.getCreatedAt(), withErrors ? b.getErrorReport() : null);
        }
    }

    public record OpeningYtdDTO(Long id, Long employeeId, String employeeCode, String employeeName, int financialYear,
                                BigDecimal grossSalary, BigDecimal taxableSalary, BigDecimal pfEmployee,
                                BigDecimal professionalTax, BigDecimal tdsDeducted, int monthsCovered,
                                BigDecimal previousEmployerIncome, BigDecimal previousEmployerTds, Long batchId,
                                LocalDateTime updatedAt) {
        public static OpeningYtdDTO of(PayrollOpeningYtd y) {
            return new OpeningYtdDTO(y.getId(), y.getEmployee().getId(), y.getEmployee().getEmployeeCode(),
                    y.getEmployee().getFullName(), y.getFinancialYear(), y.getGrossSalary(), y.getTaxableSalary(),
                    y.getPfEmployee(), y.getProfessionalTax(), y.getTdsDeducted(), y.getMonthsCovered(),
                    y.getPreviousEmployerIncome(), y.getPreviousEmployerTds(),
                    y.getBatch() != null ? y.getBatch().getId() : null, y.getUpdatedAt());
        }
    }
}
