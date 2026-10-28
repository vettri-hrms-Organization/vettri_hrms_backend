package com.haodaone.salary.service;

import com.haodaone.employee.dto.EmployeeDetailDTO;
import com.haodaone.employee.entity.Employee;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollRun;
import com.haodaone.salary.entity.SalaryComponents;
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
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Renders one {@link PayrollItem} as an A4 payslip. Same PDFBox/Standard 14
 * font approach as billing's InvoicePdfService (no font files to ship, so
 * text is limited to printable ASCII). Amounts are the item's frozen,
 * already-prorated components; the bank account is always masked.
 */
@Service
public class PayslipPdfService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    public byte[] generate(PayrollItem item) {
        PayrollRun run = item.getPayrollRun();
        Employee employee = item.getEmployee();
        YearMonth period = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());
        String periodLabel = period.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + period.getYear();
        var company = item.getCompany() != null ? item.getCompany() : run.getCompany();
        SalaryComponents c = item.getComponents();

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float left = 48;
                float right = page.getMediaBox().getWidth() - 48;
                float mid = left + (right - left) / 2;
                float y = page.getMediaBox().getHeight() - 54;

                text(content, BOLD, 18, left, y, company == null ? "Payslip" : value(company.getLegalName(), company.getName()));
                textRight(content, BOLD, 16, right, y, "PAYSLIP");
                textRight(content, REGULAR, 10, right, y - 18, "for " + periodLabel);

                y -= 40;
                line(content, left, y, right, y);

                // Employee and statutory identity, two columns.
                y -= 20;
                String[][] leftRows = {
                        {"Employee name", employee.getFullName()},
                        {"Employee code", employee.getEmployeeCode()},
                        {"Designation", employee.getDesignation() != null ? employee.getDesignation().getTitle() : "-"},
                        {"Department", employee.getDepartment() != null ? employee.getDepartment().getName() : "-"},
                        {"Date of joining", employee.getDateOfJoining() != null ? DATE_FORMAT.format(employee.getDateOfJoining()) : "-"},
                };
                String[][] rightRows = {
                        {"PAN", value(employee.getPanNumber(), "-")},
                        {"UAN (PF)", value(employee.getUanNumber(), "-")},
                        {"ESI number", value(employee.getEsiNumber(), "-")},
                        {"Bank", value(employee.getBankName(), "-")},
                        {"Account", value(EmployeeDetailDTO.maskAccountNumber(employee.getBankAccountNumber()), "-")},
                };
                for (int i = 0; i < leftRows.length; i++) {
                    float rowY = y - i * 16;
                    text(content, REGULAR, 9, left, rowY, leftRows[i][0]);
                    text(content, BOLD, 9, left + 95, rowY, value(leftRows[i][1], "-"));
                    text(content, REGULAR, 9, mid + 10, rowY, rightRows[i][0]);
                    text(content, BOLD, 9, mid + 95, rowY, rightRows[i][1]);
                }

                y -= leftRows.length * 16 + 8;
                BigDecimal paidDays = item.getPaidDays() != null ? item.getPaidDays() : BigDecimal.valueOf(period.lengthOfMonth());
                fill(content, left, y - 22, right - left, 22, 240, 244, 250);
                text(content, REGULAR, 9, left + 10, y - 15, "Days in month: " + period.lengthOfMonth());
                text(content, REGULAR, 9, left + 150, y - 15, "Paid days: " + days(paidDays));
                text(content, REGULAR, 9, left + 280, y - 15, "Loss of pay days: " + days(item.getLopDays()));
                String paidOn = item.getPaymentDate() != null ? "Paid on " + DATE_FORMAT.format(item.getPaymentDate()) : "Status: " + item.getStatus();
                textRight(content, REGULAR, 9, right - 10, y - 15, paidOn);

                // Earnings | Deductions table.
                y -= 46;
                fill(content, left, y - 22, right - left, 22, 31, 55, 92);
                content.setNonStrokingColor(1f, 1f, 1f);
                text(content, BOLD, 9, left + 10, y - 15, "EARNINGS");
                textRight(content, BOLD, 9, mid - 10, y - 15, "AMOUNT (INR)");
                text(content, BOLD, 9, mid + 10, y - 15, "DEDUCTIONS");
                textRight(content, BOLD, 9, right - 10, y - 15, "AMOUNT (INR)");
                content.setNonStrokingColor(0f, 0f, 0f);

                List<String[]> earnings = new ArrayList<>();
                earnings.add(new String[]{"Basic salary", money(c.getBasicSalary())});
                addIfPositive(earnings, "HRA", c.getHra());
                addIfPositive(earnings, "Special allowance", c.getSpecialAllowance());
                addIfPositive(earnings, "Medical allowance", c.getMedicalAllowance());
                addIfPositive(earnings, "Travel allowance", c.getTravelAllowance());
                addIfPositive(earnings, "Bonus", c.getBonus());
                addIfPositive(earnings, "Incentives", c.getIncentives());
                addIfPositive(earnings, "Overtime", c.getOvertime());
                for (var adjustment : item.getAdjustments()) {
                    if (!adjustment.isDeleted()) {
                        addIfPositive(earnings, adjustment.getLabel() + (adjustment.isTaxable() ? "" : " (non-taxable)"), adjustment.getAmount());
                    }
                }
                List<String[]> deductions = new ArrayList<>();
                addIfPositive(deductions, "Provident fund (PF)", c.getPf());
                addIfPositive(deductions, "ESI", c.getEsi());
                addIfPositive(deductions, "Professional tax", c.getProfessionalTax());
                addIfPositive(deductions, "Income tax (TDS)", c.getTds());
                addIfPositive(deductions, "Other deductions", c.getOtherDeductions());

                float rowY = y - 40;
                int rows = Math.max(earnings.size(), deductions.size());
                for (int i = 0; i < rows; i++) {
                    if (i < earnings.size()) {
                        text(content, REGULAR, 9, left + 10, rowY, earnings.get(i)[0]);
                        textRight(content, REGULAR, 9, mid - 10, rowY, earnings.get(i)[1]);
                    }
                    if (i < deductions.size()) {
                        text(content, REGULAR, 9, mid + 10, rowY, deductions.get(i)[0]);
                        textRight(content, REGULAR, 9, right - 10, rowY, deductions.get(i)[1]);
                    }
                    rowY -= 16;
                }
                line(content, mid, y - 22, mid, rowY + 4);
                line(content, left, rowY + 4, right, rowY + 4);
                rowY -= 12;
                text(content, BOLD, 9, left + 10, rowY, "Gross earnings");
                textRight(content, BOLD, 9, mid - 10, rowY, money(item.getGrossSalary()));
                text(content, BOLD, 9, mid + 10, rowY, "Total deductions");
                textRight(content, BOLD, 9, right - 10, rowY, money(item.getTotalDeductions()));

                rowY -= 34;
                fill(content, left, rowY - 10, right - left, 30, 232, 245, 236);
                text(content, BOLD, 12, left + 10, rowY, "NET PAY");
                textRight(content, BOLD, 12, right - 10, rowY, "INR " + money(item.getNetSalary()));
                text(content, REGULAR, 9, left, rowY - 30, "In words: " + amountInWords(item.getNetSalary()));

                // Employer statutory contributions: part of CTC, paid by the company, not deducted from pay.
                if (item.employerContributionsTotal().signum() > 0) {
                    float ey = rowY - 62;
                    text(content, BOLD, 9, left, ey, "EMPLOYER CONTRIBUTIONS (not deducted from your pay)");
                    ey -= 16;
                    List<String[]> employer = new ArrayList<>();
                    addIfPositive(employer, "Employer EPF", item.getEmployerEpf());
                    addIfPositive(employer, "Employer EPS (pension)", item.getEmployerEps());
                    addIfPositive(employer, "Employer ESI", item.getEmployerEsi());
                    for (String[] line : employer) {
                        text(content, REGULAR, 9, left + 10, ey, line[0]);
                        textRight(content, REGULAR, 9, mid - 10, ey, line[1]);
                        ey -= 14;
                    }
                    text(content, BOLD, 9, left + 10, ey, "Total employer contributions");
                    textRight(content, BOLD, 9, mid - 10, ey, money(item.employerContributionsTotal()));
                    if (item.getPfWages().signum() > 0) {
                        text(content, REGULAR, 8, mid + 10, ey, "PF wages: INR " + money(item.getPfWages()));
                    }
                }

                text(content, REGULAR, 8, left, 42, "This is a computer-generated payslip and does not require a signature.");
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate payslip PDF", exception);
        }
    }

    /** "Rupees Twelve Thousand Five Hundred and Fifty Paise Only", Indian grouping (lakh/crore). */
    static String amountInWords(BigDecimal amount) {
        BigDecimal value = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        boolean negative = value.signum() < 0;
        value = value.abs();
        long rupees = value.longValue();
        int paise = value.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        StringBuilder words = new StringBuilder(negative ? "Minus Rupees " : "Rupees ").append(words(rupees));
        if (paise > 0) words.append(" and ").append(words(paise)).append(" Paise");
        return words.append(" Only").toString();
    }

    private static final String[] ONES = {"Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    private static String words(long n) {
        if (n < 20) return ONES[(int) n];
        if (n < 100) return TENS[(int) (n / 10)] + (n % 10 != 0 ? " " + ONES[(int) (n % 10)] : "");
        if (n < 1000) return ONES[(int) (n / 100)] + " Hundred" + (n % 100 != 0 ? " " + words(n % 100) : "");
        if (n < 100_000) return words(n / 1000) + " Thousand" + (n % 1000 != 0 ? " " + words(n % 1000) : "");
        if (n < 10_000_000) return words(n / 100_000) + " Lakh" + (n % 100_000 != 0 ? " " + words(n % 100_000) : "");
        return words(n / 10_000_000) + " Crore" + (n % 10_000_000 != 0 ? " " + words(n % 10_000_000) : "");
    }

    private static void addIfPositive(List<String[]> rows, String label, BigDecimal amount) {
        if (amount != null && amount.signum() > 0) rows.add(new String[]{label, money(amount)});
    }

    private static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(INDIA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private static String days(BigDecimal days) {
        if (days == null) return "0";
        return days.stripTrailingZeros().toPlainString();
    }

    private void text(PDPageContentStream content, PDType1Font font, float size, float x, float y, String value) throws IOException {
        content.beginText(); content.setFont(font, size); content.newLineAtOffset(x, y); content.showText(safe(value)); content.endText();
    }
    private void textRight(PDPageContentStream content, PDType1Font font, float size, float rightX, float y, String value) throws IOException {
        float width = font.getStringWidth(safe(value)) / 1000 * size;
        text(content, font, size, rightX - width, y, value);
    }
    private void line(PDPageContentStream content, float x1, float y1, float x2, float y2) throws IOException { content.moveTo(x1, y1); content.lineTo(x2, y2); content.stroke(); }
    private void fill(PDPageContentStream content, float x, float y, float width, float height, int r, int g, int b) throws IOException { content.setNonStrokingColor(r / 255f, g / 255f, b / 255f); content.addRect(x, y, width, height); content.fill(); content.setNonStrokingColor(0f, 0f, 0f); }
    private static String value(String primary, String fallback) { return primary == null || primary.isBlank() ? fallback : primary; }
    private static String safe(String value) { return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?"); }
}
