package com.haodaone.salary.controller;

import com.haodaone.salary.dto.SalaryDashboardDTO;
import com.haodaone.salary.service.SalaryDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Salary Dashboard + Salary Reports data source. */
@RestController
@RequestMapping("/api/salary/dashboard")
@PreAuthorize("hasAuthority('SALARY_VIEW')")
public class SalaryDashboardController {

    private final SalaryDashboardService salaryDashboardService;

    public SalaryDashboardController(SalaryDashboardService salaryDashboardService) {
        this.salaryDashboardService = salaryDashboardService;
    }

    @GetMapping("/summary")
    public SalaryDashboardDTO getSummary() {
        return salaryDashboardService.getSummary();
    }
}
