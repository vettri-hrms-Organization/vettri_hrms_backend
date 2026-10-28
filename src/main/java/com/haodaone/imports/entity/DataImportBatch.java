package com.haodaone.imports.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** One committed run of a data import (V51): who imported which file, how many rows, and the per-row outcome. */
@Entity
@Table(name = "data_import_batch")
public class DataImportBatch extends BaseEntity {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_FAILED = "FAILED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "import_type", nullable = false, length = 40)
    private String importType;

    @Column(name = "file_name", length = 255)
    private String fileName;

    /** SHA-256 of the uploaded bytes - used to warn when the exact same file is imported twice. */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    /** VALID_ONLY or ALL_OR_NOTHING. */
    @Column(nullable = false, length = 20)
    private String mode;

    @Column(length = 255)
    private String options;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(length = 1000)
    private String summary;

    /** One line per problem row: "Row 4: message; message". */
    @Column(name = "error_report", columnDefinition = "TEXT")
    private String errorReport;

    @Column(name = "imported_by_name", length = 150)
    private String importedByName;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getImportType() { return importType; }
    public void setImportType(String importType) { this.importType = importType; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getImportedRows() { return importedRows; }
    public void setImportedRows(int importedRows) { this.importedRows = importedRows; }
    public int getSkippedRows() { return skippedRows; }
    public void setSkippedRows(int skippedRows) { this.skippedRows = skippedRows; }
    public int getFailedRows() { return failedRows; }
    public void setFailedRows(int failedRows) { this.failedRows = failedRows; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getErrorReport() { return errorReport; }
    public void setErrorReport(String errorReport) { this.errorReport = errorReport; }
    public String getImportedByName() { return importedByName; }
    public void setImportedByName(String importedByName) { this.importedByName = importedByName; }
}
