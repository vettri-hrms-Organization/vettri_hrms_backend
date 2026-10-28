package com.haodaone.settlement.service;

import com.haodaone.salary.service.PayrollExclusionProvider;
import com.haodaone.settlement.repository.FinalSettlementRepository;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/** Keeps employees whose Full & Final settlement is APPROVED or PAID out of later payroll runs. */
@Component
public class SettledEmployeePayrollExclusion implements PayrollExclusionProvider {

    private final FinalSettlementRepository settlementRepository;

    public SettledEmployeePayrollExclusion(FinalSettlementRepository settlementRepository) {
        this.settlementRepository = settlementRepository;
    }

    @Override
    public Set<Long> excludedEmployeeIds(Long companyId) {
        if (companyId == null) return Set.of();
        return new HashSet<>(settlementRepository.findSettledEmployeeIds(companyId));
    }
}
