package com.haodaone.employee.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.dto.PageResponse;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ConflictException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.dto.CreateEmployeeRequest;
import com.haodaone.employee.dto.EmployeeDetailDTO;
import com.haodaone.employee.dto.EmployeeSummaryDTO;
import com.haodaone.employee.dto.EmployeeOptionDTO;
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
import java.util.Locale;
import java.util.Set;

@Service
public class EmployeeService {

    /** Valid forward/lateral transitions - keeps the lifecycle honest (e.g. can't go straight from Active to nothing, or resurrect a Terminated record via this endpoint). */
    private static final Set<String> VALID_STATUSES = com.haodaone.employee.entity.EmploymentStatus.VALID_STATUSES;
    private static final String EMPLOYEE_CODE_PREFIX = "EMP";
    private static final int EMPLOYEE_CODE_DIGITS = 4;
    private static final java.time.ZoneId IST = java.time.ZoneId.of("Asia/Kolkata");

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final TeamRepository teamRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final com.haodaone.company.repository.CompanyRepository companyRepository;
    private final com.haodaone.company.repository.SubscriptionService subscriptionService;
    private final com.haodaone.security.AuthorizationService authorizationService;
    /** Nullable only for legacy unit tests built with the 9-arg constructor. */
    private final com.haodaone.auth.repository.RefreshTokenRepository refreshTokenRepository;

    public EmployeeService(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                            DesignationRepository designationRepository, TeamRepository teamRepository,
                            AuditLogService auditLogService, UserRepository userRepository, com.haodaone.company.repository.CompanyRepository companyRepository,
                            com.haodaone.company.repository.SubscriptionService subscriptionService,
                            com.haodaone.security.AuthorizationService authorizationService) {
        this(employeeRepository, departmentRepository, designationRepository, teamRepository, auditLogService,
                userRepository, companyRepository, subscriptionService, authorizationService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EmployeeService(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                            DesignationRepository designationRepository, TeamRepository teamRepository,
                            AuditLogService auditLogService, UserRepository userRepository, com.haodaone.company.repository.CompanyRepository companyRepository,
                            com.haodaone.company.repository.SubscriptionService subscriptionService,
                            com.haodaone.security.AuthorizationService authorizationService,
                            com.haodaone.auth.repository.RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
        this.subscriptionService = subscriptionService;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<EmployeeSummaryDTO> listAll(String search) {
        Long companyId = requiredTenant();
        var scope = authorizationService.resolveEmployeeIds("EMPLOYEE_VIEW");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        List<Employee> employees = scope.isEmpty()
            ? ((search == null || search.isBlank())
                ? employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)
                : employeeRepository.searchForPayroll(companyId, search.trim(), null, null))
            : employeeRepository.searchForPayrollInScope(companyId, scope.get(), search == null ? "" : search.trim(), null, null);
        return employees.stream().map(EmployeeSummaryDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeeOptionDTO> listSelectorOptions(String permissionCode) {
        Long companyId = requiredTenant();
        var scope = authorizationService.resolveEmployeeIds(permissionCode);
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        List<Employee> employees = scope.isEmpty()
                ? employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)
                : employeeRepository.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, scope.get());
        return employees.stream().map(EmployeeOptionDTO::from).toList();
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
        var scope = authorizationService.resolveEmployeeIds("EMPLOYEE_VIEW");
        if (scope.isPresent() && scope.get().isEmpty()) return new PageResponse<>(List.of(), safePage, safeSize, 0, 0);
        org.springframework.data.domain.Page<Employee> result;
        if (scope.isEmpty() && departmentId != null) {
            result = employeeRepository.searchPagedByDepartmentForCompany(companyId, term, departmentId, pageable);
        } else if (scope.isEmpty()) {
            result = term.isEmpty()
                    ? employeeRepository.findAllByCompany_IdAndDeletedFalse(companyId, pageable)
                    : employeeRepository.searchPagedForCompany(companyId, term, pageable);
        } else {
            result = new org.springframework.data.domain.PageImpl<Employee>(
                    employeeRepository.searchForPayrollInScope(companyId, scope.get(), term, departmentId, null),
                    pageable,
                    employeeRepository.countByCompany_IdAndIdInAndDeletedFalse(companyId, scope.get()));
        }

        return PageResponse.from(result, EmployeeSummaryDTO::forDirectory);
    }

    @Transactional(readOnly = true)
    public EmployeeDetailDTO getById(Long id) {
        Long companyId = requiredTenant();
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
        EmployeeDetailDTO dto = withStatutoryVisibility(EmployeeDetailDTO.from(employee), employee);
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
        userRepository.findByEmailIgnoreCaseAndCompany_IdAndDeletedFalse(saved.getEmail(), company.getId())
            .filter(user -> saved.getUser() == null)
            .ifPresent(user -> {
                saved.setUser(user);
                employeeRepository.save(saved);
            });
        auditLogService.log("Employee", saved.getId(), "CREATE",
                "Onboarded '" + saved.getFullName() + "' (" + saved.getEmployeeCode() + ")");
        publishEvent(new com.haodaone.employee.event.EmployeeCreatedEvent(company.getId(), saved.getId()));
        return withStatutoryVisibility(EmployeeDetailDTO.from(saved), saved);
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
        String previousEmail = employee.getEmail();
        String normalizedEmail = normalizeEmail(request.getEmail());
        request.setEmail(normalizedEmail);

        Employee employeeWithEmail = employeeRepository.findByEmailIgnoreCaseAndDeletedFalse(normalizedEmail).orElse(null);
        if (employeeWithEmail != null && !employeeWithEmail.getId().equals(employee.getId())) {
            throw new ConflictException("An account already exists with this email address.");
        }

        applyRequestFields(employee, request);
        if (employee.getUser() == null) {
            userRepository.findByEmailIgnoreCaseAndCompany_IdAndDeletedFalse(request.getEmail(), employee.getCompany().getId())
                .ifPresent(employee::setUser);
        }
        Employee saved = employeeRepository.save(employee);
        String auditDetails = "Profile updated for '" + saved.getFullName() + "'";
        if (!normalizedEmail.equalsIgnoreCase(previousEmail)) {
            auditDetails += "; email: " + previousEmail + " -> " + normalizedEmail;
        }
        auditLogService.log("Employee", saved.getId(), "UPDATE", auditDetails);
        return withStatutoryVisibility(EmployeeDetailDTO.from(saved), saved);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new BadRequestException("Email is required");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank() || !normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BadRequestException("Email must be valid");
        }
        return normalized;
    }

    @Transactional
    public EmployeeDetailDTO updateStatus(Long id, String newStatus, String reason) {
        return updateStatus(id, newStatus, reason, null);
    }

    /**
     * Status change with exit handling: moving to Resigned / Terminated
     * records lastWorkingDate (default today IST) and disables the login
     * (revoking refresh tokens) unless the last working date is in the
     * future - EmployeeExitScheduler disables it once that date passes.
     * Moving back to Active never re-enables login; HR does that explicitly
     * via the account-status action.
     */
    @Transactional
    public EmployeeDetailDTO updateStatus(Long id, String newStatus, String reason, java.time.LocalDate lastWorkingDate) {
        String normalizedStatus = com.haodaone.employee.entity.EmploymentStatus.normalize(newStatus);
        if (!VALID_STATUSES.contains(normalizedStatus)) {
            throw new BadRequestException("Unknown status: " + newStatus + ". Must be one of " + VALID_STATUSES);
        }

        Employee employee = findActiveOrThrow(id);
        String oldStatus = employee.getStatus();
        employee.setStatus(normalizedStatus);

        boolean exiting = isExitStatus(normalizedStatus);
        java.time.LocalDate today = java.time.LocalDate.now(IST);
        java.time.LocalDate effectiveLastWorkingDate = null;
        if (exiting) {
            effectiveLastWorkingDate = lastWorkingDate != null ? lastWorkingDate : today;
            if (employee.getDateOfJoining() != null && effectiveLastWorkingDate.isBefore(employee.getDateOfJoining())) {
                throw new BadRequestException("Last working date cannot be before the date of joining");
            }
            employee.setLastWorkingDate(effectiveLastWorkingDate);
        } else if (com.haodaone.employee.entity.EmploymentStatus.ACTIVE.equals(normalizedStatus)) {
            employee.setLastWorkingDate(null);
        }
        Employee saved = employeeRepository.save(employee);

        String details = "status: " + oldStatus + " -> " + newStatus + (reason != null && !reason.isBlank() ? " (" + reason + ")" : "")
                + (effectiveLastWorkingDate != null ? "; last working date: " + effectiveLastWorkingDate : "");
        auditLogService.log("Employee", saved.getId(), "STATUS_CHANGE", details);
        if (exiting && !isExitStatus(com.haodaone.employee.entity.EmploymentStatus.normalize(oldStatus)) && saved.getCompany() != null) {
            publishEvent(new com.haodaone.employee.event.EmployeeExitedEvent(saved.getCompany().getId(), saved.getId(),
                    normalizedStatus, effectiveLastWorkingDate));
        }

        if (exiting && !effectiveLastWorkingDate.isAfter(today) && saved.getUser() != null
                && !("DISABLED".equalsIgnoreCase(saved.getUser().getAccountStatus()) && !saved.getUser().isActive())) {
            disableLogin(saved, saved.getUser(), "employee exited (" + normalizedStatus + ")");
        }
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

        if ("DISABLED".equals(accountStatus)) {
            disableLogin(employee, user, null);
            return EmployeeDetailDTO.from(employee);
        }

        String previousStatus = user.getAccountStatus();
        user.setAccountStatus(accountStatus);
        user.setActive(true);
        userRepository.save(user);
        auditLogService.log("Employee", employee.getId(), "ACCOUNT_STATUS_CHANGED",
                "account status: " + previousStatus + " -> " + accountStatus);
        return EmployeeDetailDTO.from(employee);
    }

    /**
     * Daily offboarding sweep (called by EmployeeExitScheduler, no tenant
     * context): disables logins of Resigned / Terminated employees whose
     * last working date is before {@code today}. Returns how many were disabled.
     */
    @Transactional
    public int disableLoginsPastLastWorkingDate(java.time.LocalDate today) {
        java.util.Set<String> statuses = new java.util.HashSet<>();
        statuses.addAll(com.haodaone.employee.entity.EmploymentStatus.aliases(com.haodaone.employee.entity.EmploymentStatus.RESIGNED));
        statuses.addAll(com.haodaone.employee.entity.EmploymentStatus.aliases(com.haodaone.employee.entity.EmploymentStatus.TERMINATED));
        int disabled = 0;
        for (Employee employee : employeeRepository.findExitedWithActiveLogin(statuses, today)) {
            Long previousTenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
            try {
                if (employee.getCompany() != null) {
                    com.haodaone.tenant.TenantContext.setCurrentTenant(employee.getCompany().getId());
                }
                disableLogin(employee, employee.getUser(), "last working date " + employee.getLastWorkingDate() + " passed");
                disabled++;
            } finally {
                if (previousTenant != null) {
                    com.haodaone.tenant.TenantContext.setCurrentTenant(previousTenant);
                } else {
                    com.haodaone.tenant.TenantContext.clear();
                }
            }
        }
        return disabled;
    }

    /** Single disable path: flips the account off and revokes refresh tokens so existing sessions can't be renewed. */
    private void disableLogin(Employee employee, User user, String why) {
        String previousStatus = user.getAccountStatus();
        user.setAccountStatus("DISABLED");
        user.setActive(false);
        userRepository.save(user);
        if (refreshTokenRepository != null && user.getId() != null) {
            refreshTokenRepository.revokeAllForUser(user.getId());
        }
        auditLogService.log("Employee", employee.getId(), "ACCOUNT_STATUS_CHANGED",
                "account status: " + previousStatus + " -> DISABLED" + (why != null ? " (" + why + ")" : ""));
    }

    private static boolean isExitStatus(String normalizedStatus) {
        return com.haodaone.employee.entity.EmploymentStatus.RESIGNED.equals(normalizedStatus)
                || com.haodaone.employee.entity.EmploymentStatus.TERMINATED.equals(normalizedStatus);
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
        applyBankAndStatutoryFields(employee, request);

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findByIdAndCompany_IdAndDeletedFalse(request.getDepartmentId(), requiredTenant())
                    .orElseThrow(() -> new BadRequestException("Unknown department: " + request.getDepartmentId()));
            employee.setDepartment(department);
        } else {
            employee.setDepartment(null);
        }

        if (request.getDesignationId() != null) {
            Designation designation = designationRepository.findByIdAndCompany_IdAndDeletedFalse(request.getDesignationId(), requiredTenant())
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

    /**
     * Bank/statutory fields are optional and partial-update friendly: null
     * leaves the stored value alone (older clients and the bulk importer
     * never send them), blank clears it. A masked account number echoed
     * back from a read ("XXXX1234") is ignored rather than saved.
     */
    /**
     * Validates and applies only the bank/statutory fields of {@code request} to {@code employee} (null = leave
     * unchanged), with the same rules as create/update. Does not save. Used by the bulk data importer
     * (com.haodaone.imports), which also calls it on a scratch Employee to validate a row without writing.
     */
    public void applyBankAndStatutoryDetails(Employee employee, CreateEmployeeRequest request) {
        applyBankAndStatutoryFields(employee, request);
    }

    private void applyBankAndStatutoryFields(Employee employee, CreateEmployeeRequest request) {
        String account = request.getBankAccountNumber();
        if (account != null && !account.trim().matches("^[X*]+[0-9A-Za-z]{0,4}$")) {
            String normalized = account.replaceAll("\\s", "");
            if (!normalized.isEmpty() && !normalized.matches("^[0-9A-Za-z]{6,34}$")) {
                throw new BadRequestException("Bank account number must be 6-34 letters or digits");
            }
            employee.setBankAccountNumber(normalized.isEmpty() ? null : normalized.toUpperCase(Locale.ROOT));
        }
        String ifsc = upperOrNull(request.getBankIfsc());
        if (request.getBankIfsc() != null) {
            if (ifsc != null && !ifsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) {
                throw new BadRequestException("IFSC must be 11 characters: 4 letters, 0, then 6 letters/digits (e.g. HDFC0001234)");
            }
            employee.setBankIfsc(ifsc);
        }
        if (request.getBankName() != null) {
            employee.setBankName(request.getBankName().isBlank() ? null : request.getBankName().trim());
        }
        String pan = upperOrNull(request.getPanNumber());
        if (request.getPanNumber() != null) {
            if (pan != null && !pan.matches("^[A-Z]{5}[0-9]{4}[A-Z]$")) {
                throw new BadRequestException("PAN must be 10 characters in the format ABCDE1234F");
            }
            employee.setPanNumber(pan);
        }
        String uan = upperOrNull(request.getUanNumber());
        if (request.getUanNumber() != null) {
            if (uan != null && !uan.matches("^[0-9]{12}$")) {
                throw new BadRequestException("UAN must be 12 digits");
            }
            employee.setUanNumber(uan);
        }
        String esi = upperOrNull(request.getEsiNumber());
        if (request.getEsiNumber() != null) {
            if (esi != null && !esi.matches("^[0-9]{10,17}$")) {
                throw new BadRequestException("ESI number must be 10-17 digits");
            }
            employee.setEsiNumber(esi);
        }
    }

    private static String upperOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Setter-injected so the existing constructors (and unit tests using them) stay unchanged; null = no self bypass. */
    private com.haodaone.security.EmployeeSecurity employeeSecurity;

    @org.springframework.beans.factory.annotation.Autowired
    public void setEmployeeSecurity(com.haodaone.security.EmployeeSecurity employeeSecurity) {
        this.employeeSecurity = employeeSecurity;
    }

    /** Setter-injected (null in plain unit tests) - lifecycle events for onboarding/offboarding listeners. */
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setEventPublisher(org.springframework.context.ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private void publishEvent(Object event) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }
    }

    /**
     * Full bank/statutory identifiers go only to the employee themself or
     * someone holding SALARY_VIEW / EMPLOYEE_MANAGE over them; everyone else
     * (e.g. a manager with just EMPLOYEE_VIEW) gets the masked account number.
     */
    private EmployeeDetailDTO withStatutoryVisibility(EmployeeDetailDTO dto, Employee employee) {
        if (employee.getId() == null) return dto;
        boolean self = employeeSecurity != null && employeeSecurity.isSelf(employee.getId());
        if (self || authorizationService.isAllowed("SALARY_VIEW", "EMPLOYEE", employee.getId())
                || authorizationService.isAllowed("EMPLOYEE_MANAGE", "EMPLOYEE", employee.getId())) {
            dto.revealStatutory(employee);
        }
        return dto;
    }

    /** EMP0001, EMP0002, ... - looks at the highest existing suffix rather than a separate counter table, so it self-heals if a row is ever removed. */
    private String generateEmployeeCode() {
        int maxSuffix = employeeRepository.findEmployeeCodesByPrefix(EMPLOYEE_CODE_PREFIX).stream()
                .map(code -> code.substring(EMPLOYEE_CODE_PREFIX.length()))
                .filter(suffix -> suffix.matches("\\d+"))
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0);
        int next = maxSuffix + 1;
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
