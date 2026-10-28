package com.haodaone.settlement.service;

import com.haodaone.employee.entity.Employee;
import com.haodaone.settlement.entity.FinalSettlement;
import com.haodaone.settlement.entity.FinalSettlementLine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Full & Final settlement statement as an A4 PDF (same PDFBox / Standard 14 font approach as PayslipPdfService). */
@Service
public class SettlementPdfService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Locale INDIA = Locale.forLanguageTag("en-IN");
    private static final float ROW = 15;

    public byte[] generate(FinalSettlement s) {
        Employee e = s.getEmployee();
        var company = s.getCompany();
        List<FinalSettlementLine> earnings = new ArrayList<>();
        List<FinalSettlementLine> deductions = new ArrayList<>();
        for (FinalSettlementLine line : s.getLines()) {
            if (line.isDeleted()) continue;
            (line.isEarning() ? earnings : deductions).add(line);
        }

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float left = 48, right = page.getMediaBox().getWidth() - 48;
            float y = page.getMediaBox().getHeight() - 54;

            text(content, BOLD, 16, left, y, company == null ? "Company" : value(company.getLegalName(), company.getName()));
            textRight(content, BOLD, 13, right, y, "FULL & FINAL SETTLEMENT");
            textRight(content, REGULAR, 9, right, y - 16, "Status: " + s.getStatus() + "   Ref: FNF-" + s.getId());
            y -= 34;
            line(content, left, y, right, y);
            y -= 18;

            String[][] rows = {
                    {"Employee", e.getFullName(), "Employee code", value(e.getEmployeeCode(), "-")},
                    {"Department", e.getDepartment() != null ? e.getDepartment().getName() : "-",
                            "Designation", e.getDesignation() != null ? e.getDesignation().getTitle() : "-"},
                    {"Date of joining", date(e.getDateOfJoining()), "Last working date", date(s.getLastWorkingDate())},
                    {"Resignation date", date(s.getResignationDate()), "Exit status", value(e.getStatus(), "-")},
                    {"Service", s.getServiceYears() + "y " + s.getServiceMonths() + "m " + s.getServiceDays() + "d",
                            "PAN", value(e.getPanNumber(), "-")},
                    {"Notice period", s.getNoticePeriodDays() + " days (served " + s.getNoticeServedDays() + ")",
                            "Notice shortfall", s.isNoticeWaived() ? "Waived" : s.getNoticeShortfallDays() + " days"},
            };
            float mid = left + (right - left) / 2;
            for (String[] r : rows) {
                text(content, REGULAR, 9, left, y, r[0]);
                text(content, BOLD, 9, left + 95, y, value(r[1], "-"));
                text(content, REGULAR, 9, mid + 10, y, r[2]);
                text(content, BOLD, 9, mid + 105, y, value(r[3], "-"));
                y -= ROW;
            }

            y -= 10;
            y = table(content, "EARNINGS", earnings, left, right, y, s.getTotalEarnings());
            y -= 14;
            y = table(content, "DEDUCTIONS", deductions, left, right, y, s.getTotalDeductions());

            y -= 26;
            fill(content, left, y - 10, right - left, 30, 232, 245, 236);
            boolean recoverable = s.getNetPayable() != null && s.getNetPayable().signum() < 0;
            text(content, BOLD, 12, left + 10, y, recoverable ? "NET RECOVERABLE FROM EMPLOYEE" : "NET PAYABLE");
            textRight(content, BOLD, 12, right - 10, y, "INR " + money(s.getNetPayable() == null ? null : s.getNetPayable().abs()));
            y -= 30;
            if (s.isGratuityEligible()) {
                text(content, REGULAR, 8, left, y, "Gratuity: " + s.getGratuityYears() + " year(s) counted on last drawn basic of INR "
                        + money(s.getGratuityWage()) + " (Payment of Gratuity Act, 1972).");
                y -= 12;
            }
            if (s.getPaymentReference() != null) {
                text(content, REGULAR, 9, left, y, "Paid on " + date(s.getPaymentDate()) + ", reference " + s.getPaymentReference());
                y -= 12;
            }
            if (s.getApprovedBy() != null) {
                text(content, REGULAR, 8, left, y, "Approved by " + s.getApprovedBy() + (s.getApprovedAt() != null ? " on " + DATE.format(s.getApprovedAt()) : ""));
                y -= 12;
            }
            text(content, REGULAR, 8, left, 60, "Employee acknowledgement: I have received the above amount in full and final settlement of all my dues.");
            text(content, REGULAR, 8, left, 46, "Signature: ______________________      Date: ____________");
            text(content, REGULAR, 7, left, 32, "Computer-generated statement. Amounts in Indian Rupees.");
            content.close();
            document.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate settlement statement PDF", ex);
        }
    }

    private float table(PDPageContentStream content, String title, List<FinalSettlementLine> lines, float left, float right,
                        float y, BigDecimal total) throws IOException {
        fill(content, left, y - 20, right - left, 20, 31, 55, 92);
        content.setNonStrokingColor(1f, 1f, 1f);
        text(content, BOLD, 9, left + 10, y - 14, title);
        textRight(content, BOLD, 9, right - 10, y - 14, "AMOUNT (INR)");
        content.setNonStrokingColor(0f, 0f, 0f);
        y -= 34;
        if (lines.isEmpty()) {
            text(content, REGULAR, 9, left + 10, y, "None");
            y -= ROW;
        }
        // Keep the page within bounds: very long statements are truncated to the visible rows.
        int max = 18;
        for (int i = 0; i < lines.size() && i < max; i++) {
            FinalSettlementLine l = lines.get(i);
            String label = l.getLabel();
            if (label != null && label.length() > 85) label = label.substring(0, 82) + "...";
            text(content, REGULAR, 9, left + 10, y, label);
            textRight(content, REGULAR, 9, right - 10, y, money(l.getAmount()));
            y -= ROW;
        }
        if (lines.size() > max) {
            text(content, REGULAR, 8, left + 10, y, "+ " + (lines.size() - max) + " more line(s) included in the total");
            y -= ROW;
        }
        line(content, left, y + 10, right, y + 10);
        text(content, BOLD, 9, left + 10, y - 2, "Total " + title.toLowerCase(Locale.ROOT));
        textRight(content, BOLD, 9, right - 10, y - 2, money(total));
        return y - 8;
    }

    private static String money(BigDecimal amount) {
        NumberFormat f = NumberFormat.getNumberInstance(INDIA);
        f.setMinimumFractionDigits(2);
        f.setMaximumFractionDigits(2);
        return f.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private static String date(java.time.LocalDate d) {
        return d == null ? "-" : DATE.format(d);
    }

    private void text(PDPageContentStream c, PDType1Font font, float size, float x, float y, String value) throws IOException {
        c.beginText(); c.setFont(font, size); c.newLineAtOffset(x, y); c.showText(safe(value)); c.endText();
    }

    private void textRight(PDPageContentStream c, PDType1Font font, float size, float rightX, float y, String value) throws IOException {
        float width = font.getStringWidth(safe(value)) / 1000 * size;
        text(c, font, size, rightX - width, y, value);
    }

    private void line(PDPageContentStream c, float x1, float y1, float x2, float y2) throws IOException {
        c.moveTo(x1, y1); c.lineTo(x2, y2); c.stroke();
    }

    private void fill(PDPageContentStream c, float x, float y, float w, float h, int r, int g, int b) throws IOException {
        c.setNonStrokingColor(r / 255f, g / 255f, b / 255f); c.addRect(x, y, w, h); c.fill(); c.setNonStrokingColor(0f, 0f, 0f);
    }

    private static String value(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
