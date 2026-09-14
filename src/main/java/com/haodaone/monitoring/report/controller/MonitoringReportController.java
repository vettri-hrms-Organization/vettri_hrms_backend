package com.haodaone.monitoring.report.controller;

import com.haodaone.monitoring.report.dto.ManagementInsightsDTO;
import com.haodaone.monitoring.report.dto.ProductivitySummaryDTO;
import com.haodaone.monitoring.report.dto.ReportFilter;
import com.haodaone.monitoring.report.service.ExcelExportService;
import com.haodaone.monitoring.report.service.PdfReportService;
import com.haodaone.monitoring.report.service.ProductivityReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Requirements #3 (Activity Reports), #4 (Productivity Summary),
 * #5/#6 (Excel/PDF Export), #7 (Report Filters) and #8 (Management View) -
 * every endpoint here is filtered from real activity_session rows via
 * ProductivityReportService, nothing precomputed or mocked.
 */
@RestController
@RequestMapping("/api/monitoring/reports")
public class MonitoringReportController {

    private final ProductivityReportService productivityReportService;
    private final ExcelExportService excelExportService;
    private final PdfReportService pdfReportService;

    public MonitoringReportController(ProductivityReportService productivityReportService,
                                       ExcelExportService excelExportService,
                                       PdfReportService pdfReportService) {
        this.productivityReportService = productivityReportService;
        this.excelExportService = excelExportService;
        this.pdfReportService = pdfReportService;
    }

    /** Requirement #3 - Activity Reports (Employee ID, Employee Name, Device Name, Department, Date) and #4 - Productivity Summary calculations, same row shape for both. */
    @GetMapping("/productivity")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public List<ProductivitySummaryDTO> productivitySummary(@ModelAttribute ReportFilterParams params) {
        return productivityReportService.buildSummary(params.toFilter());
    }

    /** Requirement #8 - Management View. */
    @GetMapping("/management")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public ManagementInsightsDTO managementView(@ModelAttribute ReportFilterParams params) {
        return productivityReportService.buildManagementInsights(params.toFilter());
    }

    /** Requirement #5 - Excel Export. */
    @GetMapping("/export/excel")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public ResponseEntity<byte[]> exportExcel(@ModelAttribute ReportFilterParams params) {
        ReportFilter filter = params.toFilter();
        List<ProductivitySummaryDTO> rows = productivityReportService.buildSummary(filter);
        byte[] file = excelExportService.buildProductivityWorkbook(rows);
        String filename = "productivity-report-" + filter.getStartDate() + "_to_" + filter.getEndDate() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(file);
    }

    /** Requirement #6 - PDF Export. */
    @GetMapping("/export/pdf")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public ResponseEntity<byte[]> exportPdf(@ModelAttribute ReportFilterParams params) {
        ReportFilter filter = params.toFilter();
        List<ProductivitySummaryDTO> rows = productivityReportService.buildSummary(filter);
        byte[] file = pdfReportService.buildReport(rows, filter);
        String filename = "productivity-report-" + filter.getStartDate() + "_to_" + filter.getEndDate() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(file);
    }

    /** Query-param binding for requirement #7 (Report Filters); defaults to the trailing 30 days so no caller can trigger an unbounded table scan by omitting dates. */
    public static class ReportFilterParams {
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate startDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate endDate;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime fromTime;
        @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
        private LocalTime toTime;
        private Long employeeId;
        private String employeeCode;
        private String employeeName;
        private Long departmentId;
        private Long deviceId;
        private String deviceName;
        private String applicationName;
        private String workingMode;

        ReportFilter toFilter() {
            LocalDate end = endDate != null ? endDate : LocalDate.now();
            LocalDate start = startDate != null ? startDate : end.minusDays(29);
                return new ReportFilter(start, end, fromTime, toTime, employeeId, employeeCode, employeeName,
                    departmentId, deviceId, deviceName, applicationName, workingMode);
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public void setStartDate(LocalDate startDate) {
            this.startDate = startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public void setEndDate(LocalDate endDate) {
            this.endDate = endDate;
        }

        public LocalTime getFromTime() {
            return fromTime;
        }

        public void setFromTime(LocalTime fromTime) {
            this.fromTime = fromTime;
        }

        public LocalTime getToTime() {
            return toTime;
        }

        public void setToTime(LocalTime toTime) {
            this.toTime = toTime;
        }

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

        public String getEmployeeCode() {
            return employeeCode;
        }

        public void setEmployeeCode(String employeeCode) {
            this.employeeCode = employeeCode;
        }

        public String getEmployeeName() {
            return employeeName;
        }

        public void setEmployeeName(String employeeName) {
            this.employeeName = employeeName;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(Long departmentId) {
            this.departmentId = departmentId;
        }

        public Long getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(Long deviceId) {
            this.deviceId = deviceId;
        }

        public String getDeviceName() {
            return deviceName;
        }

        public String getApplicationName() { return applicationName; }
        public void setApplicationName(String applicationName) { this.applicationName = applicationName; }
        public String getWorkingMode() { return workingMode; }
        public void setWorkingMode(String workingMode) { this.workingMode = workingMode; }

        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }
    }
}
