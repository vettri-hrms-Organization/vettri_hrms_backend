package com.haodaone.imports.service;

import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Resolves employee codes for one validation pass (one query for the whole company) and spots repeated keys. */
final class EmployeeLookup {

    private final Map<String, Employee> byCode = new HashMap<>();
    private final Set<String> seenKeys = new HashSet<>();

    EmployeeLookup(EmployeeRepository repository, Long companyId) {
        for (Employee employee : repository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)) {
            if (employee.getEmployeeCode() != null) byCode.put(key(employee.getEmployeeCode()), employee);
        }
    }

    Iterable<Employee> all() {
        return byCode.values();
    }

    /** Looks up the "Employee Code" value, recording an error on the plan when blank/unknown. */
    Employee resolve(String code, RowPlan plan) {
        if (code == null) return null; // "required" error already recorded
        Employee employee = byCode.get(key(code));
        if (employee == null) plan.error("Employee code '" + code + "' was not found");
        return employee;
    }

    /** True (and records an error) when the same key appeared on an earlier row of this file. */
    boolean duplicate(String key, RowPlan plan, String what) {
        if (key == null) return false;
        if (!seenKeys.add(key.toLowerCase(Locale.ROOT))) {
            plan.error("Duplicate row: " + what + " appears more than once in this file");
            return true;
        }
        return false;
    }

    static boolean exited(Employee employee) {
        String status = employee.getStatus();
        return EmploymentStatus.RESIGNED.equals(status) || EmploymentStatus.TERMINATED.equals(status);
    }

    private static String key(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }
}
