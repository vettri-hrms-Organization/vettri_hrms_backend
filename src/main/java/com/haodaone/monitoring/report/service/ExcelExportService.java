package com.haodaone.monitoring.report.service;

import com.haodaone.monitoring.report.dto.AppUsageDTO;
import com.haodaone.monitoring.report.dto.ProductivitySummaryDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Requirement #5 (Excel Export). Needs org.apache.poi:poi-ooxml on the
 * classpath - add to pom.xml:
 *
 * <dependency>
 *   <groupId>org.apache.poi</groupId>
 *   <artifactId>poi-ooxml</artifactId>
 *   <version>5.2.5</version>
 * </dependency>
 *
 * One row per employee/device/day, exactly the column set requirement #5
 * asks for. Application Name / Window Title reflect that day's single
 * most-used application (see ProductivityReportService#summarize) rather
 * than exploding into one row per app - keeps one Excel row == one
 * Productivity Summary row, matching the on-screen table 1:1.
 */
@Service
public class ExcelExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private static final String[] HEADERS = {
            "Employee ID", "Employee Name", "Department", "Device Name", "Login Time", "Logout Time",
            "Total Logged In Time", "Active Time", "Idle Time", "Break Time", "Productivity %", "Classification",
            "Application Name", "Window Title"
    };

    public byte[] buildProductivityWorkbook(List<ProductivitySummaryDTO> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Productivity Summary");

            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (ProductivitySummaryDTO r : rows) {
                Row row = sheet.createRow(rowIndex++);
                List<AppUsageDTO> applications = r.getTopApplications() != null ? r.getTopApplications() : List.of();

                int c = 0;
                row.createCell(c++).setCellValue(nvl(r.getEmployeeCode()));
                row.createCell(c++).setCellValue(nvl(r.getEmployeeName()));
                row.createCell(c++).setCellValue(nvl(r.getDepartmentName()));
                row.createCell(c++).setCellValue(nvl(r.getDeviceName()));
                row.createCell(c++).setCellValue(r.getLoginTime() != null ? r.getLoginTime().format(TIME_FMT) : "");
                row.createCell(c++).setCellValue(r.getLogoutTime() != null ? r.getLogoutTime().format(TIME_FMT) : "");
                row.createCell(c++).setCellValue(formatDuration(r.getTotalLoggedInSeconds()));
                row.createCell(c++).setCellValue(formatDuration(r.getActiveSeconds()));
                row.createCell(c++).setCellValue(formatDuration(r.getIdleSeconds()));
                row.createCell(c++).setCellValue(formatDuration(r.getBreakSeconds()));
                row.createCell(c++).setCellValue(r.getProductivityPercent());
                row.createCell(c++).setCellValue(nvl(r.getProductivityClassification()));
                row.createCell(c++).setCellValue(applications.stream().map(app -> nvl(app.getApplicationName()))
                    .distinct().reduce((left, right) -> left + ", " + right).orElse(""));
                row.createCell(c).setCellValue(applications.stream()
                    .map(app -> nvl(app.getApplicationName()) + " - " + nvl(app.getWindowTitle()) + " (" + formatDuration(app.getSeconds()) + ")")
                    .reduce((left, right) -> left + "; " + right).orElse(""));
            }

            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build productivity Excel export", e);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private String nvl(String s) {
        return s != null ? s : "";
    }

    static String formatDuration(long totalSeconds) {
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        return String.format("%02d:%02d", h, m);
    }
}
