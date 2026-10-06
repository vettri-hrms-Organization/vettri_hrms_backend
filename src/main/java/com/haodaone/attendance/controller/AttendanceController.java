package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.*;
import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.attendance.repository.AttendanceRegularizationRepository;
import com.haodaone.attendance.repository.DeviceRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.attendance.service.AttendanceEventPublisher;
import com.haodaone.attendance.service.AttendanceValidationService;
import com.haodaone.attendance.service.AttendancePolicyService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.dto.EmployeeSummaryDTO;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.tenant.TenantContext;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.DayOfWeek;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private static final Logger log = LoggerFactory.getLogger(AttendanceController.class);

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceEventPublisher eventPublisher;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final AttendanceValidationService attendanceValidationService;
    private final CompanyRepository companyRepository;
    private final Clock applicationClock;
    private final com.haodaone.security.AuthorizationService authorizationService;
    private final AttendancePolicyService attendancePolicyService;
    private final AttendanceRegularizationRepository attendanceRegularizationRepository;
    private final DeviceRepository deviceRepository;
    private final WfhRequestRepository wfhRequestRepository;

    public AttendanceController(AttendanceRecordRepository attendanceRecordRepository,
                               AttendanceSessionRepository attendanceSessionRepository,
                               AttendanceEventPublisher eventPublisher,
                               EmployeeRepository employeeRepository,
                               LeaveRequestRepository leaveRequestRepository,
                               HolidayRepository holidayRepository,
                               OfficeLocationRepository officeLocationRepository,
                               AttendanceValidationService attendanceValidationService,
                               CompanyRepository companyRepository,
                               Clock applicationClock,
                               com.haodaone.security.AuthorizationService authorizationService,
                               AttendancePolicyService attendancePolicyService,
                               AttendanceRegularizationRepository attendanceRegularizationRepository,
                               DeviceRepository deviceRepository,
                               WfhRequestRepository wfhRequestRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.eventPublisher = eventPublisher;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayRepository = holidayRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.attendanceValidationService = attendanceValidationService;
        this.companyRepository = companyRepository;
        this.applicationClock = applicationClock;
        this.authorizationService = authorizationService;
        this.attendancePolicyService = attendancePolicyService;
        this.attendanceRegularizationRepository = attendanceRegularizationRepository;
        this.deviceRepository = deviceRepository;
        this.wfhRequestRepository = wfhRequestRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public List<AttendanceRecordDTO> byDate(@RequestParam(required = false) String date) {
        Long companyId = requiredTenant();
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now(applicationClock);
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        var scope = authorizationService.resolveEmployeeIds("ATTENDANCE_VIEW");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        var records = scope.isEmpty()
            ? attendanceRecordRepository.findAllByCompany_IdAndPunchTimeBetweenOrderByPunchTimeDesc(companyId, start, end)
            : attendanceRecordRepository.findScopedByCompanyAndEmployees(companyId, scope.get(), start, end);
        return records.stream()
                .map(AttendanceRecordDTO::from)
                .toList();
    }

    @GetMapping("/exceptions")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    @Transactional(readOnly = true)
    public AttendanceExceptionDTO exceptions(@RequestParam(required = false) String date) {
        Long companyId = requiredTenant();
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now(applicationClock);

        boolean isWeekend = targetDate.getDayOfWeek() == DayOfWeek.SATURDAY || targetDate.getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean isHoliday = !holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, targetDate, targetDate).isEmpty();
        boolean workingDay = !isWeekend && !isHoliday;

        if (!workingDay) {
            return new AttendanceExceptionDTO(targetDate, false, List.of());
        }

        var scope = authorizationService.resolveEmployeeIds("ATTENDANCE_VIEW");
        if (scope.isPresent() && scope.get().isEmpty()) return new AttendanceExceptionDTO(targetDate, true, List.of());
        var attendanceRows = scope.isEmpty()
            ? attendanceRecordRepository.findAllByCompany_IdAndPunchTimeBetweenOrderByPunchTimeDesc(companyId, targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay())
            : attendanceRecordRepository.findScopedByCompanyAndEmployees(companyId, scope.get(), targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay());
        Set<Long> punchedEmployeeIds = attendanceRows
                .stream()
                .filter(r -> r.getEmployee() != null && "RECEIVED".equalsIgnoreCase(r.getStatus()))
                .map(r -> r.getEmployee().getId())
                .collect(Collectors.toSet());
        List<AttendanceSession> sessions = scope.isEmpty()
                ? attendanceSessionRepository.findAllByCompany_IdAndAttendanceDateOrderByCheckInTimeDesc(companyId, targetDate)
                : attendanceSessionRepository.findAllByCompany_IdAndEmployee_IdInAndAttendanceDateOrderByCheckInTimeDesc(
                        companyId, scope.get(), targetDate);
        sessions.stream().map(AttendanceSession::getEmployee).filter(Objects::nonNull)
                .map(Employee::getId).forEach(punchedEmployeeIds::add);

        Set<Long> onApprovedLeaveIds = leaveRequestRepository.findActiveOnForCompany(companyId, targetDate).stream()
                .map(lr -> lr.getEmployee().getId())
                .collect(Collectors.toSet());

        var wfhRequests = wfhRequestRepository.findAllByCompany_IdAndWorkDateAndDeletedFalse(companyId, targetDate);
        var approvedWfhIds = wfhRequests.stream()
                .filter(request -> "APPROVED".equalsIgnoreCase(request.getStatus()))
                .map(request -> request.getEmployee().getId())
                .collect(Collectors.toSet());
        var pendingRegularizationIds = attendanceRegularizationRepository
                .findAllByCompany_IdAndAttendanceDateAndStatusAndDeletedFalseOrderByAttendanceDateAsc(
                        companyId, targetDate, "PENDING").stream()
                .map(request -> request.getEmployee().getId())
                .collect(Collectors.toSet());

        var visibleEmployees = scope.isEmpty()
            ? employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)
            : employeeRepository.findAllById(scope.get());
        List<EmployeeSummaryDTO> missingPunch = visibleEmployees.stream()
                .filter(e -> "Active".equals(e.getStatus()))
                .filter(e -> !punchedEmployeeIds.contains(e.getId()))
                .filter(e -> !onApprovedLeaveIds.contains(e.getId()))
                .map(EmployeeSummaryDTO::from).toList();
        List<AttendanceExceptionItemDTO> exceptionItems = visibleEmployees.stream()
                .filter(e -> "Active".equals(e.getStatus()))
                .filter(e -> !punchedEmployeeIds.contains(e.getId()))
                .map(employee -> {
                    if (onApprovedLeaveIds.contains(employee.getId())) {
                        return AttendanceExceptionItemDTO.from(employee, "LEAVE", "On approved leave", "LEAVE", null);
                    }
                    String method = attendancePolicyService.effectiveMethod(employee, employee.getCompany());
                    if (pendingRegularizationIds.contains(employee.getId())) {
                        return AttendanceExceptionItemDTO.from(employee, "REGULARIZATION_PENDING",
                                "Attendance regularization is awaiting review", "OFFICE", method);
                    }
                    if (approvedWfhIds.contains(employee.getId())) {
                        return AttendanceExceptionItemDTO.from(employee, "WFH_APPROVED",
                                "Remote check-in not completed", "WFH", method);
                    }
                    if ("WEB_APP_ONLY".equals(method)) {
                        return AttendanceExceptionItemDTO.from(employee, "WEB_CHECK_IN_MISSING",
                                "Web/App check-in not completed", "OFFICE", method);
                    }
                    if (employee.getBiometricDeviceId() == null || employee.getBiometricDeviceUserId() == null
                            || employee.getBiometricDeviceUserId().isBlank()) {
                        return AttendanceExceptionItemDTO.from(employee, "BIOMETRIC_ENROLLMENT_REQUIRED",
                                "Biometric enrollment or device mapping is required", "OFFICE", method);
                    }
                    var device = deviceRepository.findByIdAndCompany_IdAndDeletedFalse(
                            employee.getBiometricDeviceId(), companyId).orElse(null);
                    if (device == null || !device.isOnline()) {
                        return AttendanceExceptionItemDTO.from(employee, "BIOMETRIC_DEVICE_ISSUE",
                                "Assigned biometric device is unavailable", "OFFICE", method);
                    }
                    return AttendanceExceptionItemDTO.from(employee, "BIOMETRIC_PUNCH_MISSING",
                            "Biometric punch missing", "OFFICE", method);
                }).toList();

        return new AttendanceExceptionDTO(targetDate, true, missingPunch, exceptionItems);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('ATTENDANCE_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public List<AttendanceRecordDTO> byEmployee(@PathVariable Long employeeId) {
        Long companyId = requiredTenant();
        return attendanceRecordRepository.findAllByCompany_IdAndEmployee_IdOrderByPunchTimeDesc(companyId, employeeId).stream()
                .map(AttendanceRecordDTO::from)
                .toList();
    }

    @GetMapping("/me/history")
    @PreAuthorize("@authorizationService.canAccessOwnAttendance()")
    @Transactional(readOnly = true)
    public List<AttendanceSessionDTO> myAttendanceHistory(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        Employee employee = currentEmployee();
        Long companyId = requireCompany(employee).getId();

        if (from == null && to == null) {
            LocalDate today = LocalDate.now(applicationClock);
            from = today.withDayOfMonth(1);
            to = today.withDayOfMonth(today.lengthOfMonth());
        } else if (from == null || to == null) {
            throw new BadRequestException("Both from and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("from date must not be after to date");
        }

        List<AttendanceSessionDTO> history = new ArrayList<>(attendanceSessionRepository
                .findAllByCompany_IdAndEmployee_IdAndDeletedFalseAndAttendanceDateBetweenOrderByAttendanceDateDescCheckInTimeDesc(
                        companyId, employee.getId(), from, to)
                .stream()
                .map(this::mapSession)
                .toList());

        LocalDateTime punchStart = from.atStartOfDay();
        LocalDateTime punchEnd = to.plusDays(1).atStartOfDay().minusNanos(1);
        attendanceRecordRepository
                .findAllByCompany_IdAndEmployee_IdAndDeletedFalseAndPunchTimeBetweenOrderByPunchTimeDesc(
                        companyId, employee.getId(), punchStart, punchEnd)
                .forEach(record -> {
                    AttendanceSessionDTO dto = new AttendanceSessionDTO();
                    dto.setId(record.getId());
                    dto.setEmployeeId(employee.getId());
                    dto.setAttendanceDate(record.getPunchTime().toLocalDate());
                    if ("OUT".equalsIgnoreCase(record.getPunchType())) {
                        dto.setCheckOutTime(record.getPunchTime());
                    } else {
                        dto.setCheckInTime(record.getPunchTime());
                    }
                    dto.setStatus(record.getStatus());
                    dto.setSource(record.getSource());
                    history.add(dto);
                });

        history.sort(Comparator.comparing(AttendanceSessionDTO::getAttendanceDate).reversed()
                .thenComparing(this::historyEventTime, Comparator.nullsLast(Comparator.reverseOrder())));
        return history;
    }

    @GetMapping("/employee/{employeeId}/sessions")
    @PreAuthorize("@authorizationService.isAllowed('ATTENDANCE_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public List<AttendanceSessionDTO> employeeSessions(@PathVariable Long employeeId) {
        Long companyId = requiredTenant();
        return attendanceSessionRepository
                .findAllByCompany_IdAndEmployee_IdOrderByAttendanceDateDesc(companyId, employeeId)
                .stream().map(this::mapSession).toList();
    }

    @GetMapping("/unmapped")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceRecordDTO> unmapped() {
        return attendanceRecordRepository.findAllByCompany_IdAndEmployeeIsNullOrderByPunchTimeDesc(requiredTenant()).stream()
                .map(AttendanceRecordDTO::from)
                .toList();
    }

    @GetMapping("/stream")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public SseEmitter stream() {
        requiredTenant();
        return eventPublisher.subscribe();
    }

    @PostMapping("/check-in")
    @PreAuthorize("@authorizationService.canAccessOwnAttendance() or hasAuthority('EMPLOYEE_VIEW') or hasAuthority('ATTENDANCE_VIEW')")
    @Transactional
    public ResponseEntity<AttendanceSessionDTO> checkIn(@Valid @RequestBody AttendanceCheckInRequest request) {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);

        if (attendanceValidationService.currentActiveSession(employee, company) != null) {
            throw new BadRequestException("ALREADY_CHECKED_IN");
        }

        String normalizedSource = attendanceValidationService.normalizeSource(request.getSource());
        boolean wfh = Boolean.TRUE.equals(request.getWfh()) || "WFH".equalsIgnoreCase(request.getWorkingMode());
        attendancePolicyService.validateWebCheckIn(employee, company, wfh, LocalDate.now(applicationClock));
        log.info("CHECK-IN LOCATION REQUEST received source={} workingMode={} latitude={} longitude={} accuracy={} timestamp={} officeLocationId={}",
                normalizedSource, request.getWorkingMode(), request.getLatitude(), request.getLongitude(), request.getAccuracy(), request.getTimestamp(), request.getOfficeLocationId());
        attendanceValidationService.validateManagedDevice(employee, request.getDeviceId(), normalizedSource);

        AttendanceSession session = new AttendanceSession();
        session.setCompany(company);
        session.setEmployee(employee);
        LocalDateTime serverNow = LocalDateTime.now(applicationClock);
        session.setAttendanceDate(serverNow.toLocalDate());
        session.setCheckInTime(serverNow);
        session.setStatus("CHECKED_IN");
        session.setSource("WEB_MOBILE".equals(normalizedSource) ? "MOBILE" : "WEB");
        session.setCheckInSourceDetails(normalizedSource);
        session.setDeviceId(request.getDeviceId());
        session.setWfh(wfh);

        if (wfh) {
            attendanceValidationService.validateWfhApproval(employee, company, session.getAttendanceDate());
            session.setLocationType("WFH");
            session.setLocationValidationStatus("APPROVED_WFH");
            session.setLatitude(request.getLatitude());
            session.setLongitude(request.getLongitude());
            session.setAccuracyMeters(request.getAccuracy());
        } else {
            OfficeLocation officeLocation = attendanceValidationService.resolveOffice(company, request.getOfficeLocationId());
                attendanceValidationService.validateLocation(request.getLatitude(), request.getLongitude(), request.getAccuracy(),
                    request.getTimestamp(), officeLocation, normalizedSource);
            double distance = attendanceValidationService.calculateDistance(
                    request.getLatitude(), request.getLongitude(), officeLocation.getLatitude(), officeLocation.getLongitude());
            session.setLocationType("OFFICE");
            session.setLocationValidationStatus("VALID");
            session.setDistanceFromOfficeMeters(distance);
            session.setOfficeLocationId(officeLocation.getId());
            session.setOfficeLocationName(officeLocation.getName());
            session.setLatitude(request.getLatitude());
            session.setLongitude(request.getLongitude());
            session.setAccuracyMeters(request.getAccuracy());
        }

        session = attendanceSessionRepository.save(session);
        return ResponseEntity.ok(mapSession(session));
    }

    @PostMapping("/check-out")
    @PreAuthorize("@authorizationService.canAccessOwnAttendance() or hasAuthority('EMPLOYEE_VIEW') or hasAuthority('ATTENDANCE_VIEW')")
    @Transactional
    public ResponseEntity<AttendanceSessionDTO> checkOut(@Valid @RequestBody AttendanceCheckOutRequest request) {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);

        AttendanceSession session = attendanceSessionRepository
                .findByEmployee_IdAndCompany_IdAndStatusAndAttendanceDate(
                        employee.getId(), company.getId(), "CHECKED_IN", LocalDate.now(applicationClock))
                .orElseThrow(() -> new BadRequestException("CHECK_IN_REQUIRED"));

        if (request.getLatitude() != null && request.getLongitude() != null && session.getOfficeLocationId() != null) {
            OfficeLocation officeLocation = officeLocationRepository.findByIdAndCompany_IdAndDeletedFalse(
                    session.getOfficeLocationId(), company.getId()).orElse(null);
            if (officeLocation != null) {
                attendanceValidationService.validateLocation(request.getLatitude(), request.getLongitude(), request.getAccuracy(),
                    System.currentTimeMillis(), officeLocation, attendanceValidationService.normalizeSource(request.getSource()));
            }
        }

        LocalDateTime now = LocalDateTime.now(applicationClock);
        session.setCheckOutTime(now);
        session.setStatus("CHECKED_OUT");
        session.setCheckOutSourceDetails(attendanceValidationService.normalizeSource(request.getSource()));
        session.setDeviceId(request.getDeviceId());
        if (session.getCheckInTime() != null) {
            session.setDurationMinutes(Duration.between(session.getCheckInTime(), now).toMinutes());
        }
        attendanceSessionRepository.save(session);

        return ResponseEntity.ok(mapSession(session));
    }

    @GetMapping("/today")
    @PreAuthorize("@authorizationService.canAccessOwnAttendance() or hasAuthority('EMPLOYEE_VIEW') or hasAuthority('ATTENDANCE_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<AttendanceSessionDTO> today() {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);

        AttendanceSession session = attendanceSessionRepository
            .findTopByEmployee_IdAndCompany_IdAndAttendanceDateAndStatusInOrderByCheckInTimeDesc(
                employee.getId(), company.getId(), LocalDate.now(applicationClock), List.of("CHECKED_IN", "CHECKED_OUT"))
            .orElse(null);
        if (session == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(mapSession(session));
    }

    @GetMapping("/office-locations")
    @PreAuthorize("@authorizationService.canAccessOwnAttendance() or hasAuthority('ATTENDANCE_VIEW')")
    public List<OfficeLocationDTO> officeLocations() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) {
            companyId = requireCompany(currentEmployee()).getId();
        }
        return officeLocationRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId)
            .stream().map(OfficeLocationDTO::from).toList();
    }

    @PostMapping("/office-locations")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    @Transactional
    public ResponseEntity<OfficeLocationDTO> createOfficeLocation(@Valid @RequestBody OfficeLocationRequest request) {
        Company company = companyRepository.findById(requiredTenant())
                .orElseThrow(() -> new BadRequestException("COMPANY_NOT_FOUND"));
        OfficeLocation location = new OfficeLocation();
        location.setCompany(company);
        applyOfficeLocation(location, request);
        return ResponseEntity.status(201).body(OfficeLocationDTO.from(officeLocationRepository.save(location)));
    }

    @PutMapping("/office-locations/{id}")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    @Transactional
    public ResponseEntity<OfficeLocationDTO> updateOfficeLocation(@PathVariable Long id, @Valid @RequestBody OfficeLocationRequest request) {
        OfficeLocation location = officeLocationRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new BadRequestException("OFFICE_LOCATION_NOT_FOUND"));
        applyOfficeLocation(location, request);
        return ResponseEntity.ok(OfficeLocationDTO.from(officeLocationRepository.save(location)));
    }

    @PatchMapping("/office-locations/{id}/status")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    @Transactional
    public ResponseEntity<OfficeLocationDTO> setOfficeLocationStatus(@PathVariable Long id, @RequestParam boolean active) {
        OfficeLocation location = officeLocationRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new BadRequestException("OFFICE_LOCATION_NOT_FOUND"));
        location.setActive(active);
        return ResponseEntity.ok(OfficeLocationDTO.from(officeLocationRepository.save(location)));
    }

    private void applyOfficeLocation(OfficeLocation location, OfficeLocationRequest request) {
        location.setName(request.getName().trim());
        location.setAddress(request.getAddress());
        location.setCity(request.getCity());
        location.setState(request.getState());
        location.setCountry(request.getCountry());
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setAllowedRadiusMeters(request.getAllowedRadiusMeters());
        location.setActive(true);
    }

    @GetMapping("/team")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW') or hasAuthority('LEAVE_APPROVE')")
    public List<AttendanceSessionDTO> teamPresence(@RequestParam(required = false) String date) {
        Employee manager = currentEmployee();
        Long companyId = requireCompany(manager).getId();
        List<Long> teamIds = employeeRepository.findAllByReportingManagerIdAndDeletedFalse(manager.getId())
                .stream().map(Employee::getId).toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now(applicationClock);
        return attendanceSessionRepository.findAllByCompany_IdAndEmployee_IdInAndAttendanceDateOrderByCheckInTimeDesc(companyId, teamIds, targetDate)
                .stream().map(this::mapSession).toList();
    }

    private AttendanceSessionDTO mapSession(AttendanceSession session) {
        AttendanceSessionDTO dto = new AttendanceSessionDTO();
        dto.setId(session.getId());
        dto.setEmployeeId(session.getEmployee() != null ? session.getEmployee().getId() : null);
        dto.setEmployeeName(session.getEmployee() != null ? session.getEmployee().getFullName() : null);
        dto.setStatus(session.getStatus());
        dto.setAttendanceDate(session.getAttendanceDate());
        dto.setCheckInTime(session.getCheckInTime());
        dto.setCheckOutTime(session.getCheckOutTime());
        dto.setSource(session.getSource());
        dto.setLocationType(session.getLocationType());
        dto.setLocationValidationStatus(session.getLocationValidationStatus());
        dto.setDistanceFromOfficeMeters(session.getDistanceFromOfficeMeters());
        dto.setOfficeLocationName(session.getOfficeLocationName());
        dto.setDurationMinutes(session.getDurationMinutes());
        dto.setWfh(session.isWfh());
        return dto;
    }

    private OffsetDateTime historyEventTime(AttendanceSessionDTO record) {
        return record.getCheckInTime() != null ? record.getCheckInTime() : record.getCheckOutTime();
    }

    private Employee currentEmployee() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null ? authentication.getName() : null;
        if (username == null) {
            throw new BadRequestException("Authentication required");
        }

        Object principal = authentication != null ? authentication.getPrincipal() : null;
        return employeeRepository.findByUser_UsernameAndDeletedFalse(username)
                .or(() -> employeeRepository.findByEmailIgnoreCaseAndDeletedFalse(username))
                .or(() -> principal instanceof com.haodaone.security.CustomUserPrincipal customUserPrincipal
                        ? employeeRepository.findByUser_IdAndDeletedFalse(customUserPrincipal.getId())
                                .or(() -> employeeRepository.findByEmailIgnoreCaseAndDeletedFalse(customUserPrincipal.getUser().getEmail()))
                        : java.util.Optional.empty())
                .orElseThrow(() -> new BadRequestException("Current login is not linked to an employee"));
    }

    private Company requireCompany(Employee employee) {
        if (employee.getCompany() == null) {
            throw new BadRequestException("Employee has no company assigned");
        }
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            tenant = employee.getCompany().getId();
            TenantContext.setCurrentTenant(tenant);
        }
        if (!Objects.equals(employee.getCompany().getId(), tenant)) {
            throw new BadRequestException("Company mismatch");
        }
        return employee.getCompany();
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new IllegalStateException("Company context is required");
        }
        return tenant;
    }
}
