package com.haodaone.attendance.service;

import com.haodaone.attendance.dto.AgentAttendanceDTO;
import com.haodaone.attendance.entity.AttendanceBreak;
import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.repository.AttendanceBreakRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clock in / break / clock out from the Vettri Agent tray, for the employee the laptop is
 * assigned to (the assignment is authoritative - Windows usernames are never trusted as identity).
 *
 * Applies the same rules as the web check-in, never weaker ones:
 *   - tray clock-in is a WFH punch, so it needs that day's approved WFH request (office punches
 *     still need office location or biometric, which a laptop tray cannot prove);
 *   - BIOMETRIC_ONLY / HYBRID policies and "selfie required" are honoured (the tray then points
 *     the employee to the portal or mobile app);
 *   - the laptop must be an active managed device of the same company, assigned to that employee.
 * Refusals come back as {@code ok=false} with a plain-language message for the tray balloon.
 */
@Service
public class AgentAttendanceService {

    static final String SOURCE = "MANAGED_DEVICE";
    private static final List<String> TODAY_STATUSES = List.of("CHECKED_IN", "CHECKED_OUT");
    private static final Map<String, String> FRIENDLY = Map.of(
            "WFH_APPROVAL_REQUIRED", "Clock-in from the tray is for approved work-from-home days. On office days, use the biometric device or the Vettri app.",
            "SELFIE_REQUIRED", "Your company needs a selfie at check-in. Please clock in from the Vettri mobile app or portal.",
            "ALREADY_CHECKED_IN", "You are already clocked in.",
            "CHECK_IN_REQUIRED", "You haven't clocked in today.",
            "DEVICE_NOT_AUTHORIZED", "This laptop is not allowed to record your attendance. Please contact IT.");

    private final MonitoredDeviceRepository deviceRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceBreakRepository breakRepository;
    private final AttendanceValidationService validationService;
    private final AttendancePolicyService policyService;
    private final AttendanceSelfieService selfieService;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final String portalUrl;

    public AgentAttendanceService(MonitoredDeviceRepository deviceRepository,
                                  AttendanceSessionRepository sessionRepository,
                                  AttendanceBreakRepository breakRepository,
                                  AttendanceValidationService validationService,
                                  AttendancePolicyService policyService,
                                  AttendanceSelfieService selfieService,
                                  AuditLogService auditLogService,
                                  Clock applicationClock,
                                  @Value("${app.frontend-url:https://app.vettrihrms.in}") String portalUrl) {
        this.deviceRepository = deviceRepository;
        this.sessionRepository = sessionRepository;
        this.breakRepository = breakRepository;
        this.validationService = validationService;
        this.policyService = policyService;
        this.selfieService = selfieService;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
        this.portalUrl = portalUrl;
    }

    @Transactional(readOnly = true)
    public AgentAttendanceDTO.Status status(MonitoredDevice authenticatedDevice) {
        MonitoredDevice device = attach(authenticatedDevice);
        Employee employee = device.getEmployee();
        if (employee == null) return notAssigned(device);
        return describe(device, employee, true, null);
    }

    /** @param action CLOCK_IN, CLOCK_OUT, BREAK_START or BREAK_END */
    @Transactional(noRollbackFor = BadRequestException.class)
    public AgentAttendanceDTO.Status perform(MonitoredDevice authenticatedDevice, String action) {
        MonitoredDevice device = attach(authenticatedDevice);
        Employee employee = device.getEmployee();
        if (employee == null) return notAssigned(device);
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        try {
            switch (normalized) {
                case "CLOCK_IN" -> clockIn(device, employee);
                case "CLOCK_OUT" -> clockOut(employee);
                case "BREAK_START" -> startBreak(employee);
                case "BREAK_END" -> endBreak(employee);
                default -> throw new BadRequestException("Unknown attendance action");
            }
        } catch (BadRequestException ex) {
            return describe(device, employee, false, FRIENDLY.getOrDefault(ex.getMessage(), ex.getMessage()));
        }
        auditLogService.log("AttendanceSession", employee.getId(), "AGENT_" + normalized,
                "Tray attendance " + normalized + " from device '" + device.getDeviceName() + "'");
        return describe(device, employee, true, null);
    }

    private void clockIn(MonitoredDevice device, Employee employee) {
        Company company = employee.getCompany();
        if (validationService.currentActiveSession(employee, company) != null) throw new BadRequestException("ALREADY_CHECKED_IN");
        LocalDate today = LocalDate.now(clock);
        policyService.validateWebCheckIn(employee, company, true, today);
        selfieService.validateForCheckIn(company, null);
        validationService.validateManagedDevice(employee, device.getDeviceId(), SOURCE);
        validationService.validateWfhApproval(employee, company, today);

        LocalDateTime now = LocalDateTime.now(clock);
        AttendanceSession session = new AttendanceSession();
        session.setCompany(company);
        session.setEmployee(employee);
        session.setAttendanceDate(today);
        session.setCheckInTime(now);
        session.setStatus("CHECKED_IN");
        session.setSource("AGENT");
        session.setCheckInSourceDetails(SOURCE);
        session.setDeviceId(device.getDeviceId());
        session.setWfh(true);
        session.setLocationType("WFH");
        session.setLocationValidationStatus("APPROVED_WFH");
        sessionRepository.save(session);
    }

    private void clockOut(Employee employee) {
        AttendanceSession session = openSession(employee);
        LocalDateTime now = LocalDateTime.now(clock);
        breakRepository.findFirstByAttendanceSession_IdAndEndedAtIsNullAndDeletedFalse(session.getId())
                .ifPresent(open -> { open.setEndedAt(now); breakRepository.save(open); });
        session.setCheckOutTime(now);
        session.setStatus("CHECKED_OUT");
        session.setCheckOutSourceDetails(SOURCE);
        if (session.getCheckInTime() != null) {
            session.setDurationMinutes(Duration.between(session.getCheckInTime(), now).toMinutes());
        }
        sessionRepository.save(session);
    }

    private void startBreak(Employee employee) {
        AttendanceSession session = openSession(employee);
        if (breakRepository.findFirstByAttendanceSession_IdAndEndedAtIsNullAndDeletedFalse(session.getId()).isPresent()) {
            throw new BadRequestException("You are already on a break.");
        }
        AttendanceBreak entry = new AttendanceBreak();
        entry.setCompany(session.getCompany());
        entry.setEmployee(employee);
        entry.setAttendanceSession(session);
        entry.setStartedAt(LocalDateTime.now(clock));
        entry.setSource(SOURCE);
        breakRepository.save(entry);
    }

    private void endBreak(Employee employee) {
        AttendanceSession session = openSession(employee);
        AttendanceBreak open = breakRepository.findFirstByAttendanceSession_IdAndEndedAtIsNullAndDeletedFalse(session.getId())
                .orElseThrow(() -> new BadRequestException("You are not on a break."));
        open.setEndedAt(LocalDateTime.now(clock));
        breakRepository.save(open);
    }

    private AttendanceSession openSession(Employee employee) {
        return sessionRepository.findByEmployee_IdAndCompany_IdAndStatusAndAttendanceDate(
                        employee.getId(), employee.getCompany().getId(), "CHECKED_IN", LocalDate.now(clock))
                .orElseThrow(() -> new BadRequestException("CHECK_IN_REQUIRED"));
    }

    private AgentAttendanceDTO.Status describe(MonitoredDevice device, Employee employee, boolean ok, String message) {
        Company company = employee.getCompany();
        LocalDate today = LocalDate.now(clock);
        LocalDateTime now = LocalDateTime.now(clock);
        AttendanceSession session = sessionRepository
                .findTopByEmployee_IdAndCompany_IdAndAttendanceDateAndStatusInOrderByCheckInTimeDesc(
                        employee.getId(), company.getId(), today, TODAY_STATUSES)
                .orElse(null);

        String state = "NOT_CHECKED_IN";
        long workedMinutes = 0;
        long breakMinutes = 0;
        LocalDateTime breakStartedAt = null;
        if (session != null) {
            LocalDateTime end = session.getCheckOutTime() != null ? session.getCheckOutTime() : now;
            if (session.getCheckInTime() != null) workedMinutes = Math.max(0, Duration.between(session.getCheckInTime(), end).toMinutes());
            for (AttendanceBreak entry : breakRepository.findAllByAttendanceSession_IdAndDeletedFalseOrderByStartedAtAsc(session.getId())) {
                LocalDateTime breakEnd = entry.getEndedAt() != null ? entry.getEndedAt() : now;
                breakMinutes += Math.max(0, Duration.between(entry.getStartedAt(), breakEnd).toMinutes());
                if (entry.getEndedAt() == null) breakStartedAt = entry.getStartedAt();
            }
            workedMinutes = Math.max(0, workedMinutes - breakMinutes);
            state = "CHECKED_OUT".equals(session.getStatus()) ? "CHECKED_OUT" : breakStartedAt != null ? "ON_BREAK" : "CHECKED_IN";
        }
        return new AgentAttendanceDTO.Status(ok, state, message, employee.getFullName(), company.getName(),
                session == null ? null : session.getCheckInTime(), session == null ? null : session.getCheckOutTime(),
                breakStartedAt, workedMinutes, breakMinutes, session == null && canClockIn(employee, company, today), portalUrl);
    }

    /** Mirrors clockIn's checks without side effects, so the tray only offers "Clock in" when it would work. */
    private boolean canClockIn(Employee employee, Company company, LocalDate today) {
        try {
            policyService.validateWebCheckIn(employee, company, true, today);
            selfieService.validateForCheckIn(company, null);
            return true;
        } catch (BadRequestException ex) {
            return false;
        }
    }

    private AgentAttendanceDTO.Status notAssigned(MonitoredDevice device) {
        return new AgentAttendanceDTO.Status(false, "NOT_ASSIGNED",
                "This laptop isn't assigned to an employee yet. Ask IT to assign it in Vettri.", null,
                device.getCompany() == null ? null : device.getCompany().getName(),
                null, null, null, 0, 0, false, portalUrl);
    }

    private MonitoredDevice attach(MonitoredDevice authenticatedDevice) {
        return deviceRepository.findById(authenticatedDevice.getId())
                .orElseThrow(() -> new IllegalStateException("Authenticated device no longer exists"));
    }
}
