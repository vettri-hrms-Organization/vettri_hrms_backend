package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.*;
import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.attendance.service.AttendanceEventPublisher;
import com.haodaone.attendance.service.AttendanceValidationService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceEventPublisher eventPublisher;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final AttendanceValidationService attendanceValidationService;
    private final CompanyRepository companyRepository;

    public AttendanceController(AttendanceRecordRepository attendanceRecordRepository,
                               AttendanceSessionRepository attendanceSessionRepository,
                               AttendanceEventPublisher eventPublisher,
                               EmployeeRepository employeeRepository,
                               LeaveRequestRepository leaveRequestRepository,
                               HolidayRepository holidayRepository,
                               OfficeLocationRepository officeLocationRepository,
                               AttendanceValidationService attendanceValidationService,
                               CompanyRepository companyRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.eventPublisher = eventPublisher;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayRepository = holidayRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.attendanceValidationService = attendanceValidationService;
        this.companyRepository = companyRepository;
    }

    @GetMapping
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('ATTENDANCE_VIEW')")
    public List<AttendanceRecordDTO> byDate(@RequestParam(required = false) String date) {
        Long companyId = requiredTenant();
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return attendanceRecordRepository.findAllByCompany_IdAndPunchTimeBetweenOrderByPunchTimeDesc(companyId, start, end).stream()
                .map(AttendanceRecordDTO::from)
                .toList();
    }

    @GetMapping("/exceptions")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('ATTENDANCE_VIEW')")
    @Transactional(readOnly = true)
    public AttendanceExceptionDTO exceptions(@RequestParam(required = false) String date) {
        Long companyId = requiredTenant();
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();

        boolean isWeekend = targetDate.getDayOfWeek() == DayOfWeek.SATURDAY || targetDate.getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean isHoliday = !holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, targetDate, targetDate).isEmpty();
        boolean workingDay = !isWeekend && !isHoliday;

        if (!workingDay) {
            return new AttendanceExceptionDTO(targetDate, false, List.of());
        }

        Set<Long> punchedEmployeeIds = attendanceRecordRepository
                .findAllByCompany_IdAndPunchTimeBetweenOrderByPunchTimeDesc(companyId, targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay())
                .stream()
                .filter(r -> r.getEmployee() != null)
                .map(r -> r.getEmployee().getId())
                .collect(Collectors.toSet());

        Set<Long> onApprovedLeaveIds = leaveRequestRepository.findActiveOnForCompany(companyId, targetDate).stream()
                .map(lr -> lr.getEmployee().getId())
                .collect(Collectors.toSet());

        List<EmployeeSummaryDTO> missingPunch = employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream()
                .filter(e -> "Active".equals(e.getStatus()))
                .filter(e -> !punchedEmployeeIds.contains(e.getId()))
                .filter(e -> !onApprovedLeaveIds.contains(e.getId()))
                .map(EmployeeSummaryDTO::from)
                .toList();

        return new AttendanceExceptionDTO(targetDate, true, missingPunch);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW') or @employeeSecurity.isSelf(#employeeId)")
    public List<AttendanceRecordDTO> byEmployee(@PathVariable Long employeeId) {
        Long companyId = requiredTenant();
        return attendanceRecordRepository.findAllByCompany_IdAndEmployee_IdOrderByPunchTimeDesc(companyId, employeeId).stream()
                .map(AttendanceRecordDTO::from)
                .toList();
    }

    @GetMapping("/unmapped")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceRecordDTO> unmapped() {
        return attendanceRecordRepository.findAllByCompany_IdAndEmployeeIsNullOrderByPunchTimeDesc(requiredTenant()).stream()
                .map(AttendanceRecordDTO::from)
                .toList();
    }

    @GetMapping("/stream")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('ATTENDANCE_VIEW')")
    public SseEmitter stream() {
        requiredTenant();
        return eventPublisher.subscribe();
    }

    @PostMapping("/check-in")
    @PreAuthorize("hasRole('EMPLOYEE') or hasAuthority('EMPLOYEE_VIEW')")
    @Transactional
    public ResponseEntity<AttendanceSessionDTO> checkIn(@Valid @RequestBody AttendanceCheckInRequest request) {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);

        if (attendanceValidationService.currentActiveSession(employee, company) != null) {
            throw new BadRequestException("ALREADY_CHECKED_IN");
        }

        String normalizedSource = attendanceValidationService.normalizeSource(request.getSource());
        boolean wfh = Boolean.TRUE.equals(request.getWfh()) || "WFH".equalsIgnoreCase(request.getWorkingMode());
        attendanceValidationService.validateManagedDevice(employee, request.getDeviceId(), normalizedSource);

        AttendanceSession session = new AttendanceSession();
        session.setCompany(company);
        session.setEmployee(employee);
        session.setAttendanceDate(LocalDate.now());
        session.setCheckInTime(LocalDateTime.now());
        session.setStatus("CHECKED_IN");
        session.setSource(normalizedSource);
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
    @PreAuthorize("hasRole('EMPLOYEE') or hasAuthority('EMPLOYEE_VIEW')")
    @Transactional
    public ResponseEntity<AttendanceSessionDTO> checkOut(@Valid @RequestBody AttendanceCheckOutRequest request) {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);

        AttendanceSession session = attendanceSessionRepository
                .findByEmployee_IdAndCompany_IdAndStatusAndAttendanceDate(
                        employee.getId(), company.getId(), "CHECKED_IN", LocalDate.now())
                .orElseThrow(() -> new BadRequestException("CHECK_IN_REQUIRED"));

        if (request.getLatitude() != null && request.getLongitude() != null && session.getOfficeLocationId() != null) {
            OfficeLocation officeLocation = officeLocationRepository.findByIdAndCompany_IdAndDeletedFalse(
                    session.getOfficeLocationId(), company.getId()).orElse(null);
            if (officeLocation != null) {
                attendanceValidationService.validateLocation(request.getLatitude(), request.getLongitude(), request.getAccuracy(),
                    System.currentTimeMillis(), officeLocation, attendanceValidationService.normalizeSource(request.getSource()));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        session.setCheckOutTime(now);
        session.setStatus("CHECKED_OUT");
        session.setSource(attendanceValidationService.normalizeSource(request.getSource()));
        session.setDeviceId(request.getDeviceId());
        if (session.getCheckInTime() != null) {
            session.setDurationMinutes(Duration.between(session.getCheckInTime(), now).toMinutes());
        }
        attendanceSessionRepository.save(session);

        return ResponseEntity.ok(mapSession(session));
    }

    @GetMapping("/today")
    @PreAuthorize("hasRole('EMPLOYEE') or hasAuthority('EMPLOYEE_VIEW')")
    public ResponseEntity<AttendanceSessionDTO> today() {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);

        AttendanceSession session = attendanceSessionRepository
            .findTopByEmployee_IdAndCompany_IdAndAttendanceDateAndStatusInOrderByCheckInTimeDesc(
                employee.getId(), company.getId(), LocalDate.now(), List.of("CHECKED_IN", "CHECKED_OUT"))
            .orElse(null);
        if (session == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(mapSession(session));
    }

    @GetMapping("/office-locations")
    @PreAuthorize("hasRole('EMPLOYEE') or hasAuthority('ATTENDANCE_VIEW')")
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
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
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
        dto.setWfh(session.isWfh());
        return dto;
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
