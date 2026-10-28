package com.haodaone.salary.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.dto.TaxDeclarationDTO;
import com.haodaone.salary.entity.EmployeeTaxDeclaration;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollRun;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.repository.EmployeeTaxDeclarationRepository;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.statutory.IncomeTaxCalculator;
import com.haodaone.salary.statutory.IncomeTaxCalculator.TaxInput;
import com.haodaone.salary.statutory.IncomeTaxCalculator.TaxResult;
import com.haodaone.salary.statutory.IncomeTaxRates.Regime;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.EmployeeSecurity;
import com.haodaone.tenant.TenantContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Form 16 Part B style annual salary/tax statement per employee per FY,
 * computed from the finalized (PROCESSED/PAID) payroll lines of that FY and
 * the employee's tax declaration. It is clearly labelled as computed by
 * Vettri: Part A (TDS deposited, with the TRACES certificate number) must be
 * downloaded from TRACES by the employer. Employees can download their own;
 * SALARY_VIEW over the employee can download anyone's.
 */
@Service
public class Form16Service {

    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    private final EmployeeRepository employeeRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final EmployeeTaxDeclarationRepository declarationRepository;
    private final AuthorizationService authorizationService;
    private final EmployeeSecurity employeeSecurity;

    public Form16Service(EmployeeRepository employeeRepository, PayrollItemRepository payrollItemRepository,
                         EmployeeTaxDeclarationRepository declarationRepository, AuthorizationService authorizationService,
                         EmployeeSecurity employeeSecurity) {
        this.employeeRepository = employeeRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.declarationRepository = declarationRepository;
        this.authorizationService = authorizationService;
        this.employeeSecurity = employeeSecurity;
    }

    /** Imported opening YTD figures (mid-year switchers / previous employer). Optional. */
    private OpeningYtdProvider openingYtdProvider;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setOpeningYtdProvider(OpeningYtdProvider openingYtdProvider) {
        this.openingYtdProvider = openingYtdProvider;
    }

    @Transactional(readOnly = true)
    public PayslipService.PayslipFile forMe(Integer fy) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) throw new AccessDeniedException("Not signed in");
        Employee me = employeeRepository.findByUser_UsernameAndDeletedFalse(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Your login is not linked to an employee profile"));
        return forEmployee(me.getId(), fy);
    }

    @Transactional(readOnly = true)
    public PayslipService.PayslipFile forEmployee(Long employeeId, Integer fy) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        if (!authorizationService.isAllowed("SALARY_VIEW", "EMPLOYEE", employeeId) && !employeeSecurity.isSelf(employeeId)) {
            throw new AccessDeniedException("Not authorized to download this Form 16.");
        }
        int year = fy != null ? fy : TaxDeclarationService.currentFinancialYear() - 1;
        List<PayrollItem> items = payrollItemRepository.findFinalizedForEmployeeInFinancialYear(companyId, employeeId, year, year + 1);
        OpeningYtdProvider.OpeningYtd opening = openingYtdProvider == null ? null
                : openingYtdProvider.openingFor(companyId, employeeId, year).orElse(null);
        if (items.isEmpty() && opening == null) {
            throw new BadRequestException("No processed payroll for " + employee.getFullName() + " in " + TaxDeclarationDTO.fyLabel(year));
        }
        EmployeeTaxDeclaration declaration = declarationRepository
                .findByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(companyId, employeeId, year).orElse(null);
        Statement statement = build(employee, year, items, declaration, opening);
        String filename = String.format("form16-partB-%s-FY%d-%02d.pdf",
                employee.getEmployeeCode().replaceAll("[^A-Za-z0-9_-]", ""), year, (year + 1) % 100);
        return new PayslipService.PayslipFile(filename, render(employee, statement));
    }

    /** Annual figures behind the PDF. */
    record Statement(int fy, Regime regime, BigDecimal basic, BigDecimal hra, BigDecimal special, BigDecimal medical,
                     BigDecimal travel, BigDecimal bonus, BigDecimal incentives, BigDecimal overtime,
                     BigDecimal taxableAdjustments, BigDecimal nonTaxableAdjustments, BigDecimal gross,
                     BigDecimal pfEmployee, BigDecimal tdsDeducted, TaxResult tax, List<String[]> months,
                     EmployeeTaxDeclaration declaration, OpeningYtdProvider.OpeningYtd opening) {
    }

    static Statement build(Employee employee, int fy, List<PayrollItem> items, EmployeeTaxDeclaration d) {
        return build(employee, fy, items, d, null);
    }

    /** As above, plus imported opening YTD figures (added to taxable salary, PT, PF and TDS). */
    static Statement build(Employee employee, int fy, List<PayrollItem> items, EmployeeTaxDeclaration d,
                           OpeningYtdProvider.OpeningYtd opening) {
        BigDecimal basic = BigDecimal.ZERO, hra = BigDecimal.ZERO, special = BigDecimal.ZERO, medical = BigDecimal.ZERO,
                travel = BigDecimal.ZERO, bonus = BigDecimal.ZERO, incentives = BigDecimal.ZERO, overtime = BigDecimal.ZERO,
                taxableAdj = BigDecimal.ZERO, nonTaxableAdj = BigDecimal.ZERO, taxableGross = BigDecimal.ZERO,
                pt = BigDecimal.ZERO, pf = BigDecimal.ZERO, tds = BigDecimal.ZERO;
        List<String[]> months = new ArrayList<>();
        for (PayrollItem item : items) {
            SalaryComponents c = item.getComponents();
            basic = basic.add(c.getBasicSalary());
            hra = hra.add(c.getHra());
            special = special.add(c.getSpecialAllowance());
            medical = medical.add(c.getMedicalAllowance());
            travel = travel.add(c.getTravelAllowance());
            bonus = bonus.add(c.getBonus());
            incentives = incentives.add(c.getIncentives());
            overtime = overtime.add(c.getOvertime());
            BigDecimal taxableLines = item.sumAdjustments(true);
            taxableAdj = taxableAdj.add(taxableLines);
            nonTaxableAdj = nonTaxableAdj.add(item.sumAdjustments(false).subtract(taxableLines));
            taxableGross = taxableGross.add(item.effectiveTaxableGross());
            pt = pt.add(c.getProfessionalTax());
            pf = pf.add(c.getPf());
            tds = tds.add(c.getTds());
            PayrollRun run = item.getPayrollRun();
            months.add(new String[]{
                    Month.of(run.getPeriodMonth()).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + run.getPeriodYear(),
                    money(item.getGrossSalary()), money(item.effectiveTaxableGross()), money(c.getPf()),
                    money(c.getProfessionalTax()), money(c.getTds())});
        }
        if (opening != null) {
            taxableGross = taxableGross.add(opening.totalTaxable());
            pt = pt.add(opening.professionalTax() == null ? BigDecimal.ZERO : opening.professionalTax());
            pf = pf.add(opening.pfEmployee() == null ? BigDecimal.ZERO : opening.pfEmployee());
            tds = tds.add(opening.totalTds());
            months.add(0, new String[]{
                    "Opening YTD (" + opening.monthsCovered() + " mo, incl. prev. employer)",
                    money((opening.grossSalary() == null ? BigDecimal.ZERO : opening.grossSalary())
                            .add(opening.previousEmployerIncome() == null ? BigDecimal.ZERO : opening.previousEmployerIncome())),
                    money(opening.totalTaxable()), money(opening.pfEmployee()), money(opening.professionalTax()),
                    money(opening.totalTds())});
        }
        Regime regime = StatutoryPayrollEngine.regimeOf(d);
        // Employee PF counts towards 80C (old regime) together with declared investments.
        BigDecimal declared80C = d != null ? d.getSection80C() : BigDecimal.ZERO;
        TaxInput input = new TaxInput(fy, regime, taxableGross, basic, hra,
                d != null ? d.getRentPaid() : null, d != null && d.isMetroCity(), pt,
                declared80C.add(pf), d != null ? d.getSection80D() : null, d != null ? d.getHomeLoanInterest() : null,
                d != null ? d.getNps80ccd1b() : null, d != null ? d.getOtherDeductions() : null);
        TaxResult tax = IncomeTaxCalculator.compute(input);
        BigDecimal gross = taxableGross.add(nonTaxableAdj);
        return new Statement(fy, regime, basic, hra, special, medical, travel, bonus, incentives, overtime,
                taxableAdj, nonTaxableAdj, gross, pf, tds, tax, months, d, opening);
    }

    private byte[] render(Employee employee, Statement s) {
        var company = employee.getCompany();
        String companyName = company == null ? "-" : (company.getLegalName() != null && !company.getLegalName().isBlank()
                ? company.getLegalName() : company.getName());
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float left = 48, right = page.getMediaBox().getWidth() - 48;
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = page.getMediaBox().getHeight() - 50;
                text(content, BOLD, 15, left, y, "FORM 16 - PART B (Annexure)");
                textRight(content, BOLD, 11, right, y, TaxDeclarationDTO.fyLabel(s.fy()) + "  (AY " + (s.fy() + 1) + "-" + String.format("%02d", (s.fy() + 2) % 100) + ")");
                y -= 15;
                text(content, ITALIC, 8, left, y, "Computed by Vettri HRMS from processed payroll. Not a TRACES certificate: Part A must be downloaded from TRACES by the employer.");
                y -= 10;
                line(content, left, y, right, y);
                y -= 16;
                String[][] identity = {
                        {"Employer", companyName, "Employer TAN", "-"},
                        {"Employee", employee.getFullName(), "Employee PAN", value(employee.getPanNumber())},
                        {"Employee code", employee.getEmployeeCode(), "Tax regime", s.regime() == Regime.OLD ? "Old regime" : "New regime (115BAC)"},
                };
                for (String[] row : identity) {
                    text(content, REGULAR, 9, left, y, row[0]);
                    text(content, BOLD, 9, left + 85, y, value(row[1]));
                    text(content, REGULAR, 9, left + 290, y, row[2]);
                    text(content, BOLD, 9, left + 370, y, value(row[3]));
                    y -= 14;
                }

                TaxResult t = s.tax();
                y -= 6;
                y = section(content, left, right, y, "1. Gross salary (section 17(1))");
                y = row(content, left, right, y, "Basic salary", s.basic());
                y = row(content, left, right, y, "House rent allowance", s.hra());
                y = row(content, left, right, y, "Special allowance", s.special());
                y = row(content, left, right, y, "Medical allowance", s.medical());
                y = row(content, left, right, y, "Travel allowance", s.travel());
                y = row(content, left, right, y, "Bonus / incentives / overtime", s.bonus().add(s.incentives()).add(s.overtime()));
                y = row(content, left, right, y, "Other taxable earnings (adjustments)", s.taxableAdjustments());
                if (s.opening() != null) {
                    y = row(content, left, right, y, "Taxable salary paid before Vettri payroll (opening YTD)", s.opening().employerTaxable());
                    if (s.opening().previousEmployerIncome() != null && s.opening().previousEmployerIncome().signum() > 0) {
                        y = row(content, left, right, y, "Salary from previous employer (Form 12B)", s.opening().previousEmployerIncome());
                    }
                }
                y = totalRow(content, left, right, y, "Gross salary", t.grossSalary());
                if (s.nonTaxableAdjustments().signum() > 0) {
                    y = row(content, left, right, y, "Non-taxable reimbursements paid (not included above)", s.nonTaxableAdjustments());
                }

                y = section(content, left, right, y, "2. Exemptions u/s 10 and deductions u/s 16");
                y = row(content, left, right, y, "HRA exemption u/s 10(13A)", t.hraExemption());
                y = row(content, left, right, y, "Standard deduction u/s 16(ia)", t.standardDeduction());
                y = row(content, left, right, y, "Professional tax u/s 16(iii)", t.professionalTax());
                y = row(content, left, right, y, "Interest on housing loan u/s 24(b)", t.homeLoanInterest());

                y = section(content, left, right, y, "3. Deductions under Chapter VI-A");
                y = row(content, left, right, y, "80C (incl. employee PF " + money(s.pfEmployee()) + ", capped 1,50,000)", t.section80C());
                y = row(content, left, right, y, "80D medical insurance", t.section80D());
                y = row(content, left, right, y, "80CCD(1B) NPS", t.nps80ccd1b());
                y = row(content, left, right, y, "Other deductions declared", t.otherDeductions());
                y = totalRow(content, left, right, y, "Total Chapter VI-A", t.chapterVIATotal());

                y = section(content, left, right, y, "4. Tax computation");
                y = totalRow(content, left, right, y, "Total taxable income (rounded u/s 288A)", t.taxableIncome());
                y = row(content, left, right, y, "Tax on total income", t.taxOnIncome());
                y = row(content, left, right, y, "Rebate u/s 87A (incl. marginal relief)", t.rebate87A());
                y = row(content, left, right, y, "Tax after rebate", t.taxAfterRebate());
                y = row(content, left, right, y, "Health and education cess @ 4%", t.cess());
                y = totalRow(content, left, right, y, "Tax payable (rounded u/s 288B)", t.totalTax());
                y = row(content, left, right, y, "Tax deducted at source (s.192) by employer", s.tdsDeducted());
                BigDecimal balance = t.totalTax().subtract(s.tdsDeducted());
                totalRow(content, left, right, y, balance.signum() >= 0 ? "Balance tax payable" : "Excess deducted (refundable on filing)", balance.abs());
                if (s.regime() == Regime.NEW) {
                    text(content, ITALIC, 8, left, 70, "New regime: HRA, 80C, 80D, 24(b) and other old-regime deductions are not allowed; only the standard deduction applies.");
                }
                text(content, REGULAR, 8, left, 42, "Surcharge (income above Rs 50 lakh) and income from other employers/sources are not included. Verify before filing.");
            }

            PDPage monthsPage = new PDPage(PDRectangle.A4);
            document.addPage(monthsPage);
            try (PDPageContentStream content = new PDPageContentStream(document, monthsPage)) {
                float y = monthsPage.getMediaBox().getHeight() - 50;
                text(content, BOLD, 13, left, y, "TDS deducted by month - " + TaxDeclarationDTO.fyLabel(s.fy()));
                textRight(content, REGULAR, 9, right, y, employee.getFullName() + " (" + employee.getEmployeeCode() + ")");
                y -= 24;
                String[] header = {"Month", "Gross", "Taxable", "Employee PF", "Prof. tax", "TDS"};
                float[] cols = {left + 10, left + 190, left + 270, left + 350, left + 420, right - 10};
                fill(content, left, y - 6, right - left, 20, 31, 55, 92);
                content.setNonStrokingColor(1f, 1f, 1f);
                text(content, BOLD, 9, cols[0], y, header[0]);
                for (int i = 1; i < header.length; i++) textRight(content, BOLD, 9, cols[i], y, header[i]);
                content.setNonStrokingColor(0f, 0f, 0f);
                y -= 22;
                for (String[] m : s.months()) {
                    text(content, REGULAR, 9, cols[0], y, m[0]);
                    for (int i = 1; i < m.length; i++) textRight(content, REGULAR, 9, cols[i], y, m[i]);
                    y -= 16;
                }
                line(content, left, y + 10, right, y + 10);
                y -= 6;
                text(content, BOLD, 9, cols[0], y, "Total");
                textRight(content, BOLD, 9, cols[5], y, money(s.tdsDeducted()));
                y -= 40;
                text(content, ITALIC, 8, left, y, "This statement is computed by Vettri HRMS and is not a substitute for Form 16 Part A,");
                text(content, ITALIC, 8, left, y - 12, "which the employer must download from TRACES (www.tdscpc.gov.in) after filing Form 24Q.");
                text(content, REGULAR, 8, left, 42, "Computer-generated statement; no signature required.");
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate Form 16 PDF", exception);
        }
    }

    private float section(PDPageContentStream content, float left, float right, float y, String title) throws IOException {
        y -= 8;
        fill(content, left, y - 5, right - left, 17, 240, 244, 250);
        text(content, BOLD, 9.5f, left + 6, y, title);
        return y - 18;
    }

    private float row(PDPageContentStream content, float left, float right, float y, String label, BigDecimal amount) throws IOException {
        text(content, REGULAR, 9, left + 14, y, label);
        textRight(content, REGULAR, 9, right - 6, y, money(amount));
        return y - 13;
    }

    private float totalRow(PDPageContentStream content, float left, float right, float y, String label, BigDecimal amount) throws IOException {
        text(content, BOLD, 9, left + 14, y, label);
        textRight(content, BOLD, 9, right - 6, y, money(amount));
        return y - 14;
    }

    static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(INDIA);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private static String value(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private void text(PDPageContentStream content, PDType1Font font, float size, float x, float y, String value) throws IOException {
        content.beginText(); content.setFont(font, size); content.newLineAtOffset(x, y); content.showText(safe(value)); content.endText();
    }

    private void textRight(PDPageContentStream content, PDType1Font font, float size, float rightX, float y, String value) throws IOException {
        float width = font.getStringWidth(safe(value)) / 1000 * size;
        text(content, font, size, rightX - width, y, value);
    }

    private void line(PDPageContentStream content, float x1, float y1, float x2, float y2) throws IOException {
        content.moveTo(x1, y1); content.lineTo(x2, y2); content.stroke();
    }

    private void fill(PDPageContentStream content, float x, float y, float width, float height, int r, int g, int b) throws IOException {
        content.setNonStrokingColor(r / 255f, g / 255f, b / 255f); content.addRect(x, y, width, height); content.fill(); content.setNonStrokingColor(0f, 0f, 0f);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
