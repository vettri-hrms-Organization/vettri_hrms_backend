package com.haodaone.employee.event;

/**
 * Published by EmployeeService after a new employee record is saved.
 * Listeners (e.g. onboarding) should use @TransactionalEventListener so
 * they only act once the creating transaction has committed.
 */
public record EmployeeCreatedEvent(Long companyId, Long employeeId) {}
