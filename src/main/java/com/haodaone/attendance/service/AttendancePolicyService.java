package com.haodaone.attendance.service;

import com.haodaone.attendance.dto.AttendanceContextDTO;
import com.haodaone.attendance.dto.AttendanceBiometricDeviceDTO;
import com.haodaone.attendance.dto.AttendancePolicyDTO;
import com.haodaone.attendance.dto.EmployeeAttendanceConfigurationDTO;
import com.haodaone.attendance.dto.EmployeeAttendanceConfigurationRequest;
import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.entity.Device;
import com.haodaone.attendance.entity.WfhRequest;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.attendance.repository.DeviceRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.List;

@Service
public class AttendancePolicyService {

    private static final Set<String> METHODS = Set.of("BIOMETRIC_ONLY", "WEB_APP_ONLY", "HYBRID");

    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final DeviceRepository deviceRepository;
    private final WfhRequestRepository wfhRequestRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AttendancePolicyService(CompanyRepository companyRepository,
                                   EmployeeRepository employeeRepository,
                                   DeviceRepository deviceRepository,
                                   WfhRequestRepository wfhRequestRepository,
                                   AttendanceSessionRepository attendanceSessionRepository,
                                   AttendanceRecordRepository attendanceRecordRepository,
                                   AuditLogService auditLogService,
                                   Clock clock) {
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.deviceRepository = deviceRepository;
        this.wfhRequestRepository = wfhRequestRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AttendancePolicyDTO getPolicy() {
        return toPolicy(company(requiredTenant()));
    }

    @Transactional
    public AttendancePolicyDTO updatePolicy(AttendancePolicyDTO request) {
        Company company = company(requiredTenant());
        String method = normalizeMethod(request.attendanceMethod());
        if (request.gracePeriodMinutes() < 0 || request.gracePeriodMinutes() > 60) {
            throw new BadRequestException("Grace period must be between 0 and 60 minutes.");
        }
        String previous = describe(company);
        company.setAttendanceMethod(method);
        company.setManualRegularizationEnabled(request.manualRegularizationEnabled());
        company.setRegularizationApprovalRequired(request.regularizationApprovalRequired());
        company.setAttendanceGraceMinutes(request.gracePeriodMinutes());
        Company saved = companyRepository.save(company);
        auditLogService.log("AttendancePolicy", saved.getId(), "UPDATE",
                "Attendance policy changed from [" + previous + "] to [" + describe(saved) + "]");
        return toPolicy(saved);
    }

    @Transactional(readOnly = true)
    public EmployeeAttendanceConfigurationDTO getEmployeeConfiguration(Long employeeId) {
        Long companyId = requiredTenant();
        Employee employee = employee(employeeId, companyId);
        Device device = employee.getBiometricDeviceId() == null ? null
                : deviceRepository.findByIdAndCompany_IdAndDeletedFalse(employee.getBiometricDeviceId(), companyId).orElse(null);
        return new EmployeeAttendanceConfigurationDTO(
                employee.getAttendanceMethodOverride(),
                effectiveMethod(employee, company(requiredTenant())),
                employee.getBiometricDeviceId(),
                device == null ? null : device.getDeviceName(),
                employee.getBiometricDeviceUserId(),
                employee.getBiometricDeviceId() != null
                        && employee.getBiometricDeviceUserId() != null
                        && !employee.getBiometricDeviceUserId().isBlank()
                        && device != null);
    }

    @Transactional
    public EmployeeAttendanceConfigurationDTO updateEmployeeConfiguration(
            Long employeeId, EmployeeAttendanceConfigurationRequest request) {
        Long companyId = requiredTenant();
        Employee employee = employee(employeeId, companyId);
        String override = request.attendanceMethodOverride() == null
                || request.attendanceMethodOverride().isBlank()
                ? null : normalizeMethod(request.attendanceMethodOverride());
        Long deviceId = request.biometricDeviceId();
        Device device = null;
        if (deviceId != null) {
            device = deviceRepository.findByIdAndCompany_IdAndDeletedFalse(deviceId, companyId)
                    .orElseThrow(() -> new BadRequestException("Biometric device is not active in this company."));
        }
        String userId = request.biometricDeviceUserId() == null
                ? null : request.biometricDeviceUserId().trim();
        if (userId != null && userId.isEmpty()) userId = null;
        if (deviceId != null && userId == null) {
            throw new BadRequestException("Enter a biometric user ID for the selected device.");
        }

        String previous = "method=" + employee.getAttendanceMethodOverride()
                + ",device=" + employee.getBiometricDeviceId()
                + ",biometricUserIdConfigured=" + (employee.getBiometricDeviceUserId() != null);
        employee.setAttendanceMethodOverride(override);
        employee.setBiometricDeviceId(deviceId);
        employee.setBiometricDeviceUserId(userId);
        Employee saved = employeeRepository.save(employee);
        auditLogService.log("EmployeeAttendanceConfiguration", saved.getId(), "UPDATE",
                "Attendance configuration changed from [" + previous + "] to [method=" + override
                        + ",device=" + deviceId + ",biometricUserIdConfigured=" + (userId != null) + "]");
        return getEmployeeConfiguration(saved.getId());
    }

    @Transactional(readOnly = true)
    public List<AttendanceBiometricDeviceDTO> biometricDevices(Long employeeId) {
        Long companyId = requiredTenant();
        employee(employeeId, companyId);
        return deviceRepository.findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(companyId)
                .stream().map(AttendanceBiometricDeviceDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public AttendanceContextDTO getCurrentContext() {
        Employee employee = currentEmployee();
        Company company = employee.getCompany();
        LocalDate today = LocalDate.now(clock);
        String method = effectiveMethod(employee, company);
        WfhRequest wfhRequest = wfhRequestRepository
                .findByEmployee_IdAndCompany_IdAndWorkDateAndDeletedFalse(employee.getId(), company.getId(), today)
                .orElse(null);
        boolean approvedWfh = wfhRequest != null && "APPROVED".equalsIgnoreCase(wfhRequest.getStatus());
        boolean allowedWeb = !"BIOMETRIC_ONLY".equals(method)
                && ("WEB_APP_ONLY".equals(method) || approvedWfh);
        boolean requiresBiometric = !"WEB_APP_ONLY".equals(method)
                && (!approvedWfh || "BIOMETRIC_ONLY".equals(method));
        AttendanceSession session = attendanceSessionRepository
                .findTopByEmployee_IdAndCompany_IdAndAttendanceDateAndStatusInOrderByCheckInTimeDesc(
                        employee.getId(), company.getId(), today, java.util.List.of("CHECKED_IN", "CHECKED_OUT"))
                .orElse(null);
        AttendanceRecord biometricPunch = attendanceRecordRepository
                .findAllByCompany_IdAndEmployee_IdOrderByPunchTimeDesc(company.getId(), employee.getId())
                .stream()
                .filter(row -> row.getPunchTime() != null && today.equals(row.getPunchTime().toLocalDate())
                        && "RECEIVED".equalsIgnoreCase(row.getStatus()))
                .findFirst()
                .orElse(null);
        String attendanceStatus = session != null ? session.getStatus()
                : biometricPunch != null ? "BIOMETRIC_PUNCHED" : "NOT_MARKED";
        boolean enrolled = employee.getBiometricDeviceId() != null
                && employee.getBiometricDeviceUserId() != null
                && !employee.getBiometricDeviceUserId().isBlank();
        return new AttendanceContextDTO(
                today,
                method,
                approvedWfh ? "WFH" : "OFFICE",
                wfhRequest == null ? "NONE" : wfhRequest.getStatus(),
                allowedWeb,
                requiresBiometric,
                enrolled,
                attendanceStatus,
                biometricPunch == null ? null : biometricPunch.getPunchType(),
                biometricPunch == null ? null : biometricPunch.getPunchTime().toString());
    }

    @Transactional(readOnly = true)
    public String effectiveMethod(Employee employee, Company company) {
        String override = employee.getAttendanceMethodOverride();
        return override == null || override.isBlank()
                ? normalizeMethod(company.getAttendanceMethod()) : normalizeMethod(override);
    }

    @Transactional(readOnly = true)
    public boolean isBiometricAllowed(Employee employee, Company company) {
        return !"WEB_APP_ONLY".equals(effectiveMethod(employee, company));
    }

    public void validateWebCheckIn(Employee employee, Company company, boolean wfh, LocalDate date) {
        String method = effectiveMethod(employee, company);
        if ("BIOMETRIC_ONLY".equals(method)) {
            throw new BadRequestException("Web check-in is not enabled for your organization's attendance policy.");
        }
        if (wfh) {
            if (!"APPROVED".equalsIgnoreCase(wfhRequestRepository
                    .findByEmployee_IdAndCompany_IdAndWorkDateAndDeletedFalse(employee.getId(), company.getId(), date)
                    .map(request -> request.getStatus()).orElse(""))) {
                throw new BadRequestException("WFH_APPROVAL_REQUIRED");
            }
            return;
        }
        if ("HYBRID".equals(method)) {
            throw new BadRequestException("Biometric attendance is required for office attendance.");
        }
    }

    private String normalizeMethod(String method) {
        String normalized = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(normalized)) {
            throw new BadRequestException("Attendance method must be BIOMETRIC_ONLY, WEB_APP_ONLY, or HYBRID.");
        }
        return normalized;
    }

    private AttendancePolicyDTO toPolicy(Company company) {
        String method = normalizeMethod(company.getAttendanceMethod());
        return new AttendancePolicyDTO(
                method,
                company.isManualRegularizationEnabled(),
                company.isRegularizationApprovalRequired(),
                company.getAttendanceGraceMinutes(),
                !"WEB_APP_ONLY".equals(method),
                !"BIOMETRIC_ONLY".equals(method));
    }

    private String describe(Company company) {
        return "method=" + company.getAttendanceMethod()
                + ",regularizationEnabled=" + company.isManualRegularizationEnabled()
                + ",approvalRequired=" + company.isRegularizationApprovalRequired()
                + ",graceMinutes=" + company.getAttendanceGraceMinutes();
    }

    private Employee employee(Long employeeId, Long companyId) {
        return employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new BadRequestException("Employee not found in this company."));
    }

    private Company company(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("COMPANY_NOT_FOUND"));
    }

    private Employee currentEmployee() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new BadRequestException("Authentication required");
        }
        Employee employee = employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName())
                .or(() -> employeeRepository.findByEmailIgnoreCaseAndDeletedFalse(authentication.getName()))
                .orElseThrow(() -> new BadRequestException("Current login is not linked to an employee."));
        if (employee.getCompany() == null || !employee.getCompany().getId().equals(requiredTenant())) {
            throw new BadRequestException("Employee is not in this company.");
        }
        return employee;
    }

    private Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required.");
        return companyId;
    }
}
