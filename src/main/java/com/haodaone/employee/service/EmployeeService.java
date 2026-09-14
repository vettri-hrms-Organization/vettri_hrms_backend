package com.haodaone.employee.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.dto.PageResponse;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.dto.CreateEmployeeRequest;
import com.haodaone.employee.dto.EmployeeDetailDTO;
import com.haodaone.employee.dto.EmployeeSummaryDTO;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.entity.Department;
import com.haodaone.org.entity.Designation;
import com.haodaone.org.entity.Team;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.org.repository.DesignationRepository;
import com.haodaone.org.repository.TeamRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
public class EmployeeService {

    /** Valid forward/lateral transitions - keeps the lifecycle honest (e.g. can't go straight from Active to nothing, or resurrect a Terminated record via this endpoint). */
    private static final Set<String> VALID_STATUSES = com.haodaone.employee.entity.EmploymentStatus.VALID_STATUSES;
    private static final String EMPLOYEE_CODE_PREFIX = "EMP";
    private static final int EMPLOYEE_CODE_DIGITS = 4;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final TeamRepository teamRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final com.haodaone.company.repository.CompanyRepository companyRepository;
    private final com.haodaone.company.repository.SubscriptionService subscriptionService;

    public EmployeeService(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                            DesignationRepository designationRepository, TeamRepository teamRepository,
                            AuditLogService auditLogService, UserRepository userRepository, com.haodaone.company.repository.CompanyRepository companyRepository,
                            com.haodaone.company.repository.SubscriptionService subscriptionService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
        this.subscriptionService = subscriptionService;
    }

    @Transactional(readOnly = true)
    public List<EmployeeSummaryDTO> listAll(String search) {
        Long companyId = requiredTenant();
        List<Employee> employees = (search == null || search.isBlank())
                ? employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)
                : employeeRepository.searchForPayroll(companyId, search.trim(), null, null);
        return employees.stream().map(EmployeeSummaryDTO::from).toList();
    }

    /**
     * Paged directory listing - what EmployeeController#listAll actually
     * calls now. listAll(String) above is kept as-is since other callers
     * (dropdowns for "reporting manager" pickers etc.) want the full,
     * unpaginated list and would need rework to handle paging themselves;
     * this is purely additive.
     */
    @Transactional(readOnly = true)
    public PageResponse<EmployeeSummaryDTO> listPaged(String search, Long departmentId, int page, int size) {
        Long companyId = requiredTenant();
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        var pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "firstName"));

        String term = (search == null) ? "" : search.trim();
        var result = departmentId != null
                ? employeeRepository.searchPagedByDepartmentForCompany(companyId, term, departmentId, pageable)
                : (term.isEmpty() ? employeeRepository.findAllByCompany_IdAndDeletedFalse(companyId, pageable) : employeeRepository.searchPagedForCompany(companyId, term, pageable));

        return PageResponse.from(result, EmployeeSummaryDTO::from);
    }

    @Transactional(readOnly = true)
    public EmployeeDetailDTO getById(Long id) {
        Long companyId = requiredTenant();
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
        EmployeeDetailDTO dto = EmployeeDetailDTO.from(employee);
        List<EmployeeSummaryDTO> directReports = employeeRepository.findAllByReportingManagerIdAndDeletedFalse(id).stream()
                .filter(e -> companyId.equals(e.getCompany() == null ? null : e.getCompany().getId()))
                .map(EmployeeSummaryDTO::from)
                .toList();
        dto.setDirectReports(directReports);
        return dto;
    }

    @Transactional
    public EmployeeDetailDTO create(CreateEmployeeRequest request) {
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An employee with email '" + request.getEmail() + "' already exists");
        }
        Long currentTenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
        if (currentTenant == null) {
            throw new BadRequestException("Company context is required");
        }
        return createWithCode(request, generateEmployeeCode(), companyRepository.findById(currentTenant)
            .orElseThrow(() -> new BadRequestException("Company not found: " + currentTenant)));
    }

    /** Shared persistence path for the normal form and the validated bulk importer. */
    @Transactional
    public EmployeeDetailDTO createWithCode(CreateEmployeeRequest request, String employeeCode,
                                            com.haodaone.company.entity.Company company) {
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An employee with email '" + request.getEmail() + "' already exists");
        }
        Employee employee = new Employee();
        employee.setEmployeeCode(employeeCode);
        applyRequestFields(employee, request);
        String requestedStatus = request.getStatus() != null ? com.haodaone.employee.entity.EmploymentStatus.normalize(request.getStatus()) : com.haodaone.employee.entity.EmploymentStatus.ACTIVE;
        employee.setStatus(com.haodaone.employee.entity.EmploymentStatus.VALID_STATUSES.contains(requestedStatus) ? requestedStatus : com.haodaone.employee.entity.EmploymentStatus.ACTIVE);
        employee.setCompany(company);

        // Enforce employee limits from subscription if company known
        if (employee.getCompany() != null && employee.getCompany().getId() != null) {
            long companyId = employee.getCompany().getId();
            long currentCount = employeeRepository.countByCompany_IdAndDeletedFalse(companyId);
            subscriptionService.ensureEmployeeLimitNotExceeded(companyId, currentCount);
        }

        Employee saved = employeeRepository.save(employee);
        auditLogService.log("Employee", saved.getId(), "CREATE",
                "Onboarded '" + saved.getFullName() + "' (" + saved.getEmployeeCode() + ")");
        return EmployeeDetailDTO.from(saved);
    }

    /**
     * Links an existing Employee profile to a User login account (see
     * Employee.user's javadoc - the two are deliberately separate
     * entities). Used by the Recruitment module's auto-onboarding flow
     * once it creates a login for a newly-hired candidate.
     */
    @Transactional
    public void linkUserAccount(Long employeeId, Long userId) {
        Long companyId = requiredTenant();
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + employeeId));
        User user = userRepository.findByIdAndCompanyIdAndDeletedFalse(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        employee.setUser(user);
        employeeRepository.save(employee);
        auditLogService.log("Employee", employeeId, "USER_LINKED",
                "Linked login account '" + user.getUsername() + "' to '" + employee.getFullName() + "'");
    }

    @Transactional
    public EmployeeDetailDTO update(Long id, CreateEmployeeRequest request) {
        Employee employee = findActiveOrThrow(id);

        if (!employee.getEmail().equalsIgnoreCase(request.getEmail()) && employeeRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An employee with email '" + request.getEmail() + "' already exists");
        }

        applyRequestFields(employee, request);
        Employee saved = employeeRepository.save(employee);
        auditLogService.log("Employee", saved.getId(), "UPDATE", "Profile updated for '" + saved.getFullName() + "'");
        return EmployeeDetailDTO.from(saved);
    }

    @Transactional
    public EmployeeDetailDTO updateStatus(Long id, String newStatus, String reason) {
        String normalizedStatus = com.haodaone.employee.entity.EmploymentStatus.normalize(newStatus);
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new BadRequestException("Unknown status: " + newStatus + ". Must be one of " + VALID_STATUSES);
        }

        Employee employee = findActiveOrThrow(id);
        String oldStatus = employee.getStatus();
        employee.setStatus(normalizedStatus);
        Employee saved = employeeRepository.save(employee);

        String details = "status: " + oldStatus + " -> " + newStatus + (reason != null && !reason.isBlank() ? " (" + reason + ")" : "");
        auditLogService.log("Employee", saved.getId(), "STATUS_CHANGE", details);
        return EmployeeDetailDTO.from(saved);
    }

    @Transactional
    public EmployeeDetailDTO setAccountStatus(Long id, String accountStatus) {
        if (!"ACTIVE".equals(accountStatus) && !"DISABLED".equals(accountStatus)) {
            throw new BadRequestException("Account status must be ACTIVE or DISABLED");
        }

        Employee employee = findActiveOrThrow(id);
        User user = employee.getUser();
        if (user == null) {
            throw new BadRequestException("This employee has no login account");
        }

        String previousStatus = user.getAccountStatus();
        user.setAccountStatus(accountStatus);
        user.setActive("ACTIVE".equals(accountStatus));
        userRepository.save(user);
        auditLogService.log("Employee", employee.getId(), "ACCOUNT_STATUS_CHANGED",
                "account status: " + previousStatus + " -> " + accountStatus);
        return EmployeeDetailDTO.from(employee);
    }

    private void applyRequestFields(Employee employee, CreateEmployeeRequest request) {
        employee.setFirstName(request.getFirstName());
        employee.setLastName(request.getLastName());
        employee.setEmail(request.getEmail());
        employee.setPhone(request.getPhone());
        employee.setDateOfBirth(request.getDateOfBirth());
        employee.setGender(request.getGender());
        employee.setDateOfJoining(request.getDateOfJoining());
        employee.setEmploymentType(request.getEmploymentType() != null ? request.getEmploymentType() : "FULL_TIME");
        employee.setAddress(request.getAddress());
        employee.setEmergencyContactName(request.getEmergencyContactName());
        employee.setEmergencyContactPhone(request.getEmergencyContactPhone());

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findByIdAndCompany_IdAndDeletedFalse(request.getDepartmentId(), requiredTenant())
                    .orElseThrow(() -> new BadRequestException("Unknown department: " + request.getDepartmentId()));
            employee.setDepartment(department);
        } else {
            employee.setDepartment(null);
        }

        if (request.getDesignationId() != null) {
            Designation designation = designationRepository.findById(request.getDesignationId())
                    .orElseThrow(() -> new BadRequestException("Unknown designation: " + request.getDesignationId()));
            employee.setDesignation(designation);
        } else {
            employee.setDesignation(null);
        }

        if (request.getTeamId() != null) {
            Team team = teamRepository.findByIdAndCompany_IdAndDeletedFalse(request.getTeamId(), requiredTenant())
                    .orElseThrow(() -> new BadRequestException("Unknown team: " + request.getTeamId()));
            employee.setTeam(team);
        } else {
            employee.setTeam(null);
        }

        if (request.getReportingManagerId() != null) {
            if (employee.getId() != null && request.getReportingManagerId().equals(employee.getId())) {
                throw new BadRequestException("An employee cannot report to themselves");
            }
            Long companyId = requiredTenant();
            Employee manager = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getReportingManagerId(), companyId)
                    .orElseThrow(() -> new BadRequestException("Unknown reporting manager: " + request.getReportingManagerId()));
            employee.setReportingManager(manager);
        } else {
            employee.setReportingManager(null);
        }
    }

    /** EMP0001, EMP0002, ... - looks at the highest existing suffix rather than a separate counter table, so it self-heals if a row is ever removed. */
    private String generateEmployeeCode() {
        Integer maxSuffix = employeeRepository.findMaxEmployeeCodeSuffix(EMPLOYEE_CODE_PREFIX, EMPLOYEE_CODE_PREFIX.length());
        int next = (maxSuffix == null ? 0 : maxSuffix) + 1;
        return EMPLOYEE_CODE_PREFIX + String.format("%0" + EMPLOYEE_CODE_DIGITS + "d", next);
    }

    @Transactional
    public EmployeeDetailDTO setBiometricMapping(Long id, String deviceUserId) {
        Employee employee = findActiveOrThrow(id);
        employee.setBiometricDeviceUserId(deviceUserId != null && deviceUserId.isBlank() ? null : deviceUserId);
        Employee saved = employeeRepository.save(employee);
        auditLogService.log("Employee", saved.getId(), "UPDATE",
                "Biometric device mapping set to " + (deviceUserId == null || deviceUserId.isBlank() ? "(none)" : deviceUserId));
        return EmployeeDetailDTO.from(saved);
    }

    private Employee findActiveOrThrow(Long id) {
        Long companyId = requiredTenant();
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
        if (employee.isDeleted()) {
            throw new ResourceNotFoundException("Employee not found: " + id);
        }
        return employee;
    }

    private Long requiredTenant() {
        Long tenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new BadRequestException("Company context is required");
        }
        return tenant;
    }
}
