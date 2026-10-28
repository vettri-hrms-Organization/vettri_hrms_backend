package com.haodaone.salary.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollItemStatus;
import com.haodaone.salary.entity.PayrollRun;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.PayrollRunRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.EmployeeSecurity;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payslip PDF download and the bank transfer file for a payroll run.
 * Payslips follow the same access rule as EmployeeSalaryController#getDetail
 * (SALARY_VIEW over that employee, or the employee themself), except that
 * an employee only gets their own payslip once the run is PROCESSED/PAID -
 * a DRAFT line can still change.
 */
@Service
public class PayslipService {

    public record PayslipFile(String filename, byte[] content) {
    }

    private final PayrollItemRepository payrollItemRepository;
    private final PayrollRunRepository payrollRunRepository;
    private final PayslipPdfService payslipPdfService;
    private final AuthorizationService authorizationService;
    private final EmployeeSecurity employeeSecurity;

    public PayslipService(PayrollItemRepository payrollItemRepository, PayrollRunRepository payrollRunRepository,
                          PayslipPdfService payslipPdfService, AuthorizationService authorizationService,
                          EmployeeSecurity employeeSecurity) {
        this.payrollItemRepository = payrollItemRepository;
        this.payrollRunRepository = payrollRunRepository;
        this.payslipPdfService = payslipPdfService;
        this.authorizationService = authorizationService;
        this.employeeSecurity = employeeSecurity;
    }

    @Transactional(readOnly = true)
    public PayslipFile payslipPdf(Long itemId) {
        PayrollItem item = payrollItemRepository.findByIdAndPayrollRun_Company_IdAndDeletedFalse(itemId, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Payslip not found: " + itemId));
        Long employeeId = item.getEmployee().getId();
        boolean finalized = PayrollItemStatus.PROCESSED.equals(item.getStatus()) || PayrollItemStatus.PAID.equals(item.getStatus());
        boolean allowed = authorizationService.isAllowed("SALARY_VIEW", "EMPLOYEE", employeeId)
                || (finalized && employeeSecurity.isSelf(employeeId));
        if (!allowed) {
            throw new AccessDeniedException("Not authorized to download this payslip.");
        }
        PayrollRun run = item.getPayrollRun();
        String filename = String.format("payslip-%s-%d-%02d.pdf",
                item.getEmployee().getEmployeeCode().replaceAll("[^A-Za-z0-9_-]", ""), run.getPeriodYear(), run.getPeriodMonth());
        return new PayslipFile(filename, payslipPdfService.generate(item));
    }

    /**
     * One row per non-held item: what a bank's bulk-upload template needs.
     * Full account numbers - the endpoint is SALARY_MANAGE (organization) only.
     */
    @Transactional(readOnly = true)
    public PayslipFile bankTransferCsv(Long runId) {
        if (!authorizationService.hasOrganizationScope("SALARY_MANAGE")) {
            throw new AccessDeniedException("SALARY_MANAGE requires ORGANIZATION scope.");
        }
        Long companyId = requiredTenant();
        PayrollRun run = payrollRunRepository.findByIdAndCompany_IdAndDeletedFalse(runId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + runId));
        List<PayrollItem> items = payrollItemRepository
                .findByPayrollRun_Company_IdAndPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(companyId, runId);

        StringBuilder csv = new StringBuilder("Employee Name,Employee Code,Bank Name,Account Number,IFSC,Net Pay\r\n");
        for (PayrollItem item : items) {
            if (PayrollItemStatus.ON_HOLD.equals(item.getStatus())) continue;
            Employee e = item.getEmployee();
            BigDecimal net = item.getNetSalary() == null ? BigDecimal.ZERO : item.getNetSalary();
            csv.append(cell(e.getFullName())).append(',')
                    .append(cell(e.getEmployeeCode())).append(',')
                    .append(cell(e.getBankName())).append(',')
                    .append(cell(e.getBankAccountNumber())).append(',')
                    .append(cell(e.getBankIfsc())).append(',')
                    .append(net.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString())
                    .append("\r\n");
        }
        String filename = String.format("bank-transfer-%d-%02d.csv", run.getPeriodYear(), run.getPeriodMonth());
        return new PayslipFile(filename, csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Quoted CSV cell; leading =,+,-,@ are neutralised so a spreadsheet never evaluates them. */
    static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
