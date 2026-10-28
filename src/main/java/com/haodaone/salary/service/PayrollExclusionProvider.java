package com.haodaone.salary.service;

import java.util.Set;

/**
 * Extension point: employees a module wants kept out of new payroll runs (e.g. employees whose
 * Full & Final settlement is APPROVED/PAID - they are paid through the settlement instead).
 * PayrollService.createRun skips every id returned by any bean of this type.
 */
public interface PayrollExclusionProvider {

    Set<Long> excludedEmployeeIds(Long companyId);
}
