package com.haodaone.attendance.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "work_session")
public class WorkSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private com.haodaone.company.entity.Company company;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "login_time")
    private LocalDateTime loginTime;

    @Column(name = "logout_time")
    private LocalDateTime logoutTime;

    @Column(name = "working_mode", nullable = false, length = 20)
    private String workingMode; // OFFICE or WFH

    @Column(name = "status", nullable = false, length = 20)
    private String status; // ACTIVE, COMPLETED

    @Column(name = "total_working_minutes")
    private Integer totalWorkingMinutes;

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public com.haodaone.company.entity.Company getCompany() {
        return company;
    }

    public void setCompany(com.haodaone.company.entity.Company company) {
        this.company = company;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public LocalDateTime getLogoutTime() {
        return logoutTime;
    }

    public void setLogoutTime(LocalDateTime logoutTime) {
        this.logoutTime = logoutTime;
    }

    public String getWorkingMode() {
        return workingMode;
    }

    public void setWorkingMode(String workingMode) {
        this.workingMode = workingMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getTotalWorkingMinutes() {
        return totalWorkingMinutes;
    }

    public void setTotalWorkingMinutes(Integer totalWorkingMinutes) {
        this.totalWorkingMinutes = totalWorkingMinutes;
    }
}
