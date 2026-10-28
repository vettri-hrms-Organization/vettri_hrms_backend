package com.haodaone.employee.event;

import java.time.LocalDate;

/**
 * Published by EmployeeService when an employee's status moves into
 * Resigned / Terminated from a non-exit status.
 */
public record EmployeeExitedEvent(Long companyId, Long employeeId, String status, LocalDate lastWorkingDate) {}
