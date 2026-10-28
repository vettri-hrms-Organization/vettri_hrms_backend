package com.haodaone.salary.adjustment;

import com.haodaone.attendance.shift.dto.OvertimePayable;
import com.haodaone.attendance.shift.service.OvertimeService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** Pays approved overtime (attendance/shift) as a taxable earnings line, one line per entry. */
@Component
public class OvertimeAdjustmentProvider implements PayrollAdjustmentProvider {

    static final String SOURCE_TYPE = "OVERTIME_ENTRY";

    private final OvertimeService overtimeService;

    public OvertimeAdjustmentProvider(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    @Override
    public List<PayrollAdjustment> adjustmentsFor(Long companyId, Long employeeId, YearMonth period) {
        return overtimeService.approvedUnpaidOvertime(companyId, employeeId, period).stream()
                .filter(payable -> payable.amount() != null && payable.amount().signum() > 0)
                .map(OvertimeAdjustmentProvider::toAdjustment)
                .toList();
    }

    private static PayrollAdjustment toAdjustment(OvertimePayable payable) {
        BigDecimal hours = BigDecimal.valueOf(payable.minutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return new PayrollAdjustment("OVERTIME", "Overtime " + payable.workDate() + " (" + hours.stripTrailingZeros().toPlainString() + " h)",
                payable.amount(), true, SOURCE_TYPE, payable.id());
    }

    @Override
    public Set<String> sourceTypes() {
        return Set.of(SOURCE_TYPE);
    }

    @Override
    public void markSettled(List<PayrollAdjustment> adjustments, Long payrollItemId) {
        overtimeService.markPaid(adjustments.stream().map(PayrollAdjustment::sourceId).toList(), payrollItemId);
    }
}
