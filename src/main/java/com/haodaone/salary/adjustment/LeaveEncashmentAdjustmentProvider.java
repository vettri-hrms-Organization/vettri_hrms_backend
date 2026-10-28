package com.haodaone.salary.adjustment;

import com.haodaone.leave.service.LeaveEncashmentService;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** Pays approved leave encashments as a taxable earnings line (encashment while in service is salary income). */
@Component
public class LeaveEncashmentAdjustmentProvider implements PayrollAdjustmentProvider {

    static final String SOURCE_TYPE = "LEAVE_ENCASHMENT";

    private final LeaveEncashmentService encashmentService;

    public LeaveEncashmentAdjustmentProvider(LeaveEncashmentService encashmentService) {
        this.encashmentService = encashmentService;
    }

    @Override
    public List<PayrollAdjustment> adjustmentsFor(Long companyId, Long employeeId, YearMonth period) {
        return encashmentService.approvedUnpaidEncashments(companyId, employeeId, period).stream()
                .filter(payable -> payable.amount() != null && payable.amount().signum() > 0)
                .map(payable -> new PayrollAdjustment("LEAVE_ENCASHMENT",
                        "Leave encashment - " + payable.leaveTypeName() + " (" + payable.days() + " d)",
                        payable.amount(), true, SOURCE_TYPE, payable.id()))
                .toList();
    }

    @Override
    public Set<String> sourceTypes() {
        return Set.of(SOURCE_TYPE);
    }

    @Override
    public void markSettled(List<PayrollAdjustment> adjustments, Long payrollItemId) {
        encashmentService.markPaid(adjustments.stream().map(PayrollAdjustment::sourceId).toList(), payrollItemId);
    }
}
