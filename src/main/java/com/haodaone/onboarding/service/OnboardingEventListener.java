package com.haodaone.onboarding.service;

import com.haodaone.employee.event.EmployeeCreatedEvent;
import com.haodaone.employee.event.EmployeeExitedEvent;
import com.haodaone.onboarding.entity.ChecklistConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts the default onboarding checklist when an employee is created and
 * the default exit checklist when they move to Resigned / Terminated. Runs
 * after the employee transaction commits (in its own transaction) and never
 * lets a checklist failure affect the employee operation itself.
 */
@Component
public class OnboardingEventListener {

    private static final Logger log = LoggerFactory.getLogger(OnboardingEventListener.class);

    private final OnboardingService onboardingService;

    public OnboardingEventListener(OnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmployeeCreated(EmployeeCreatedEvent event) {
        if (event.companyId() == null || event.employeeId() == null) return;
        try {
            onboardingService.autoStart(event.companyId(), event.employeeId(), ChecklistConstants.TYPE_ONBOARDING, null);
        } catch (RuntimeException e) {
            log.warn("Could not auto-start onboarding checklist for employee {}: {}", event.employeeId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmployeeExited(EmployeeExitedEvent event) {
        if (event.companyId() == null || event.employeeId() == null) return;
        try {
            onboardingService.autoStart(event.companyId(), event.employeeId(), ChecklistConstants.TYPE_EXIT, event.lastWorkingDate());
        } catch (RuntimeException e) {
            log.warn("Could not auto-start exit checklist for employee {}: {}", event.employeeId(), e.getMessage());
        }
    }
}
