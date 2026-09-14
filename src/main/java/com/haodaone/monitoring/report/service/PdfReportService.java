package com.haodaone.monitoring.report.service;

import com.haodaone.monitoring.report.dto.AppUsageDTO;
import com.haodaone.monitoring.report.dto.ProductivitySummaryDTO;
import com.haodaone.monitoring.report.dto.ReportFilter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Requirement #6 (PDF Export). Needs org.apache.pdfbox:pdfbox on the
 * classpath - add to pom.xml:
 *
 * <dependency>
 *   <groupId>org.apache.pdfbox</groupId>
 *   <artifactId>pdfbox</artifactId>
 *   <version>3.0.2</version>
 * </dependency>
 *
 * No image asset for the company logo ships with this module (none existed
 * in the repo) - the header instead renders a "HAODAONE" wordmark in the
 * brand color as a text-based lockup. Swap in a real logo by loading a
 * PNG/JPEG with PDImageXObject.createFromFile(...) and drawing it in
 * drawHeader() below once a logo asset is added under
 * src/main/resources/branding/.
 */
@Service
public class PdfReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
    private static final float MARGIN = 40f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN;

    public byte[] buildReport(List<ProductivitySummaryDTO> rows, ReportFilter filter) {
        try (PDDocument document = new PDDocument()) {
            PdfCursor cursor = new PdfCursor(document);
            cursor.newPage();

            drawHeader(cursor, "Employee Productivity Report");
            drawFilterSummary(cursor, filter, rows.size());

            Map<Long, List<ProductivitySummaryDTO>> byEmployee = groupByEmployee(rows);

            if (byEmployee.isEmpty()) {
                cursor.text("No activity data found for the selected filters.", cursor.body, 11, false);
            }

            boolean first = true;
            for (List<ProductivitySummaryDTO> days : byEmployee.values()) {
                if (!first) {
                    cursor.newPage();
                    drawHeader(cursor, "Employee Productivity Report");
                }
                first = false;
                drawEmployeeSection(cursor, days);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build PDF report", e);
        }
    }

    private Map<Long, List<ProductivitySummaryDTO>> groupByEmployee(List<ProductivitySummaryDTO> rows) {
        Map<Long, List<ProductivitySummaryDTO>> map = new LinkedHashMap<>();
        for (ProductivitySummaryDTO r : rows) {
            Long key = r.getEmployeeId() != null ? r.getEmployeeId() : -1L;
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        return map;
    }

    private void drawHeader(PdfCursor cursor, String title) throws IOException {
        cursor.rect(0, cursor.y - 46, PAGE_WIDTH, 56, 0.11f, 0.16f, 0.32f);
        cursor.textAt(MARGIN, cursor.y - 28, "HAODAONE", cursor.bold, 18, 1, 1, 1);
        cursor.textAt(MARGIN, cursor.y - 42, "HR operations, without the admin chaos.", cursor.body, 8, 0.8f, 0.85f, 0.95f);
        cursor.textAt(PAGE_WIDTH - MARGIN - cursor.textWidth(title, cursor.bold, 13), cursor.y - 28, title, cursor.bold, 13, 1, 1, 1);
        String generated = "Generated: " + java.time.LocalDateTime.now().format(DATE_FMT.withLocale(java.util.Locale.ENGLISH));
        cursor.textAt(PAGE_WIDTH - MARGIN - cursor.textWidth(generated, cursor.body, 8), cursor.y - 42, generated, cursor.body, 8, 0.8f, 0.85f, 0.95f);
        cursor.y -= 68;
    }

    private void drawFilterSummary(PdfCursor cursor, ReportFilter filter, int rowCount) throws IOException {
        String range = filter.getStartDate().format(DATE_FMT) + " – " + filter.getEndDate().format(DATE_FMT);
        StringBuilder scope = new StringBuilder("Date Range: ").append(range);
        if (filter.getEmployeeName() != null) scope.append("  |  Employee: ").append(filter.getEmployeeName());
        if (filter.getEmployeeCode() != null) scope.append("  |  Employee ID: ").append(filter.getEmployeeCode());
        if (filter.getDeviceName() != null) scope.append("  |  Device: ").append(filter.getDeviceName());
        scope.append("  |  Records: ").append(rowCount);
        cursor.text(scope.toString(), cursor.body, 9, false);
        cursor.y -= 6;
        cursor.hr();
        cursor.y -= 10;
    }

    private void drawEmployeeSection(PdfCursor cursor, List<ProductivitySummaryDTO> days) throws IOException {
        ProductivitySummaryDTO any = days.get(0);

        cursor.sectionTitle("Employee Details");
        cursor.keyValueRow("Employee ID", nvl(any.getEmployeeCode()), "Employee Name", nvl(any.getEmployeeName()));
        cursor.keyValueRow("Department", nvl(any.getDepartmentName()), "Designation", nvl(any.getDesignationTitle()));
        cursor.y -= 6;

        cursor.sectionTitle("Device Details");
        String deviceNames = String.join(", ", days.stream().map(ProductivitySummaryDTO::getDeviceName).distinct().toList());
        cursor.keyValueRow("Device(s)", deviceNames, "Days in Report", String.valueOf(days.size()));
        cursor.y -= 6;

        cursor.sectionTitle("Daily Summary & Productivity");
        String[] headers = {"Date", "Login", "Logout", "Logged In", "Active", "Idle", "Break", "Prod. %"};
        float[] widths = {0.13f, 0.16f, 0.16f, 0.12f, 0.11f, 0.10f, 0.10f, 0.12f};
        List<String[]> tableRows = new ArrayList<>();
        long sumActive = 0, sumIdle = 0, sumBreak = 0, sumLoggedIn = 0;
        double sumProductivity = 0;
        for (ProductivitySummaryDTO d : days) {
            tableRows.add(new String[]{
                    d.getDate().format(DateTimeFormatter.ofPattern("dd-MMM")),
                    d.getLoginTime() != null ? d.getLoginTime().format(TIME_FMT) : "-",
                    d.getLogoutTime() != null ? d.getLogoutTime().format(TIME_FMT) : "-",
                    ExcelExportService.formatDuration(d.getTotalLoggedInSeconds()),
                    ExcelExportService.formatDuration(d.getActiveSeconds()),
                    ExcelExportService.formatDuration(d.getIdleSeconds()),
                    ExcelExportService.formatDuration(d.getBreakSeconds()),
                    String.format("%.1f%% (%s)", d.getProductivityPercent(), d.getProductivityClassification())
            });
            sumActive += d.getActiveSeconds();
            sumIdle += d.getIdleSeconds();
            sumBreak += d.getBreakSeconds();
            sumLoggedIn += d.getTotalLoggedInSeconds();
            sumProductivity += d.getProductivityPercent();
        }
        cursor.table(headers, widths, tableRows);
        cursor.y -= 8;

        double avgProductivity = days.isEmpty() ? 0 : sumProductivity / days.size();
        cursor.sectionTitle("Productivity Summary (Period Totals)");
        cursor.keyValueRow("Total Logged In Time", ExcelExportService.formatDuration(sumLoggedIn),
                "Total Active Time", ExcelExportService.formatDuration(sumActive));
        cursor.keyValueRow("Total Idle Time", ExcelExportService.formatDuration(sumIdle),
                "Total Break Time", ExcelExportService.formatDuration(sumBreak));
        cursor.keyValueRow("Average Productivity", String.format("%.1f%%", avgProductivity), "", "");
        cursor.y -= 6;

        cursor.sectionTitle("Application Usage Breakdown");
        Map<String, Long> appTotals = new LinkedHashMap<>();
        Map<String, Boolean> appIdleFlag = new LinkedHashMap<>();
        for (ProductivitySummaryDTO d : days) {
            if (d.getTopApplications() == null) continue;
            for (AppUsageDTO app : d.getTopApplications()) {
                String key = app.getApplicationName() + " - " + app.getWindowTitle();
                appTotals.merge(key, app.getSeconds(), Long::sum);
                appIdleFlag.put(key, app.isIdle());
            }
        }
        List<String[]> appRows = appTotals.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> new String[]{e.getKey(), ExcelExportService.formatDuration(e.getValue()),
                    Boolean.TRUE.equals(appIdleFlag.get(e.getKey())) ? "Idle" : "Active"})
                .toList();
        if (appRows.isEmpty()) {
            cursor.text("No application usage recorded for this period.", cursor.body, 9, true);
        } else {
            cursor.table(new String[]{"Application", "Time Spent", "Type"}, new float[]{0.5f, 0.3f, 0.2f}, appRows);
        }
        cursor.y -= 8;

        cursor.sectionTitle("Idle Time Analysis");
        double idleShare = sumLoggedIn > 0 ? (sumIdle * 100.0) / sumLoggedIn : 0;
        double breakShare = sumLoggedIn > 0 ? (sumBreak * 100.0) / sumLoggedIn : 0;
        cursor.keyValueRow("Idle Time Share", String.format("%.1f%% of logged-in time", idleShare),
                "Break Time Share", String.format("%.1f%% of logged-in time", breakShare));
        cursor.text("Idle Time = inactivity under 15 minutes at the desk. Break Time = inactivity of 15 minutes or longer.",
                cursor.body, 8, true);
        cursor.y -= 14;
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    /** Minimal hand-rolled layout helper wrapping PDFBox's low-level content stream API - handles pagination, simple tables, and key/value rows so the report-building methods above stay declarative. */
    private static class PdfCursor {
        final PDDocument document;
        final PDFont bold;
        final PDFont body;
        PDPageContentStream stream;
        float y;

        PdfCursor(PDDocument document) {
            this.document = document;
            this.bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            this.body = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        }

        void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        void ensureSpace(float needed) throws IOException {
            if (y - needed < MARGIN) {
                newPage();
            }
        }

        float textWidth(String s, PDFont font, float size) throws IOException {
            return font.getStringWidth(s) / 1000 * size;
        }

        void text(String s, PDFont font, float size, boolean muted) throws IOException {
            ensureSpace(16);
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(muted ? 0.45f : 0.15f, muted ? 0.45f : 0.15f, muted ? 0.45f : 0.2f);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(safe(s));
            stream.endText();
            y -= size + 6;
        }

        void textAt(float x, float yPos, String s, PDFont font, float size, float r, float g, float b) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(r, g, b);
            stream.newLineAtOffset(x, yPos);
            stream.showText(safe(s));
            stream.endText();
        }

        void rect(float x, float yTop, float w, float h, float r, float g, float b) throws IOException {
            stream.setNonStrokingColor(r, g, b);
            stream.addRect(x, yTop, w, h);
            stream.fill();
        }

        void hr() throws IOException {
            stream.setStrokingColor(0.85f, 0.85f, 0.88f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(PAGE_WIDTH - MARGIN, y);
            stream.stroke();
        }

        void sectionTitle(String title) throws IOException {
            ensureSpace(24);
            stream.setNonStrokingColor(0.11f, 0.16f, 0.32f);
            stream.addRect(MARGIN, y - 12, 3, 12);
            stream.fill();
            stream.beginText();
            stream.setFont(bold, 11);
            stream.setNonStrokingColor(0.11f, 0.16f, 0.32f);
            stream.newLineAtOffset(MARGIN + 8, y - 11);
            stream.showText(safe(title));
            stream.endText();
            y -= 22;
        }

        void keyValueRow(String k1, String v1, String k2, String v2) throws IOException {
            ensureSpace(16);
            float col2 = MARGIN + CONTENT_WIDTH / 2f;
            stream.beginText();
            stream.setFont(bold, 8.5f);
            stream.setNonStrokingColor(0.4f, 0.4f, 0.45f);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(safe(k1.toUpperCase()));
            stream.endText();
            if (k2 != null && !k2.isBlank()) {
                stream.beginText();
                stream.setFont(bold, 8.5f);
                stream.newLineAtOffset(col2, y);
                stream.showText(safe(k2.toUpperCase()));
                stream.endText();
            }
            y -= 12;
            stream.beginText();
            stream.setFont(body, 10.5f);
            stream.setNonStrokingColor(0.15f, 0.15f, 0.2f);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(safe(v1));
            stream.endText();
            if (v2 != null && !v2.isBlank()) {
                stream.beginText();
                stream.setFont(body, 10.5f);
                stream.newLineAtOffset(col2, y);
                stream.showText(safe(v2));
                stream.endText();
            }
            y -= 18;
        }

        void table(String[] headers, float[] widthFractions, List<String[]> rows) throws IOException {
            float rowHeight = 18f;
            ensureSpace(rowHeight * 2);

            float[] colX = new float[headers.length + 1];
            colX[0] = MARGIN;
            for (int i = 0; i < headers.length; i++) {
                colX[i + 1] = colX[i] + CONTENT_WIDTH * widthFractions[i];
            }

            // Header row
            stream.setNonStrokingColor(0.93f, 0.94f, 0.97f);
            stream.addRect(MARGIN, y - rowHeight, CONTENT_WIDTH, rowHeight);
            stream.fill();
            for (int i = 0; i < headers.length; i++) {
                stream.beginText();
                stream.setFont(bold, 8);
                stream.setNonStrokingColor(0.11f, 0.16f, 0.32f);
                stream.newLineAtOffset(colX[i] + 4, y - rowHeight + 6);
                stream.showText(safe(headers[i]));
                stream.endText();
            }
            y -= rowHeight;

            boolean shade = false;
            for (String[] row : rows) {
                ensureSpace(rowHeight);
                if (shade) {
                    stream.setNonStrokingColor(0.975f, 0.98f, 0.99f);
                    stream.addRect(MARGIN, y - rowHeight, CONTENT_WIDTH, rowHeight);
                    stream.fill();
                }
                shade = !shade;
                for (int i = 0; i < row.length && i < headers.length; i++) {
                    stream.beginText();
                    stream.setFont(body, 8);
                    stream.setNonStrokingColor(0.2f, 0.2f, 0.25f);
                    stream.newLineAtOffset(colX[i] + 4, y - rowHeight + 6);
                    stream.showText(safe(truncate(row[i], (int) (widthFractions[i] * CONTENT_WIDTH / 4.2f))));
                    stream.endText();
                }
                y -= rowHeight;
            }
            stream.setStrokingColor(0.85f, 0.85f, 0.88f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(PAGE_WIDTH - MARGIN, y);
            stream.stroke();
            y -= 10;
        }

        private String truncate(String s, int maxChars) {
            if (s == null) return "";
            return s.length() > maxChars ? s.substring(0, Math.max(0, maxChars - 1)) + "…" : s;
        }

        /** PDFBox's Helvetica encoding (WinAnsi) can't represent every Unicode char an app's window title might contain - strip anything outside it rather than throwing mid-render. */
        private String safe(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length());
            for (char c : s.toCharArray()) {
                sb.append(c < 256 ? c : '?');
            }
            return sb.toString();
        }
    }
}
