package com.haodaone.attendance.shift.service;

import com.haodaone.attendance.shift.dto.RosterCopyWeekRequest;
import com.haodaone.attendance.shift.dto.RosterDTO;
import com.haodaone.attendance.shift.dto.RosterUpdateRequest;
import com.haodaone.attendance.shift.dto.ShiftAssignmentDTO;
import com.haodaone.attendance.shift.dto.ShiftAssignmentRequest;
import com.haodaone.attendance.shift.dto.ShiftDTO;
import com.haodaone.attendance.shift.dto.ShiftRequest;
import com.haodaone.attendance.shift.dto.ShiftSettingsDTO;
import com.haodaone.attendance.shift.dto.ShiftTimes;
import com.haodaone.attendance.shift.entity.AttendanceShiftSettings;
import com.haodaone.attendance.shift.entity.ShiftAssignment;
import com.haodaone.attendance.shift.entity.ShiftRoster;
import com.haodaone.attendance.shift.entity.WorkShift;
import com.haodaone.attendance.shift.repository.AttendanceShiftSettingsRepository;
import com.haodaone.attendance.shift.repository.ShiftAssignmentRepository;
import com.haodaone.attendance.shift.repository.ShiftRosterRepository;
import com.haodaone.attendance.shift.repository.WorkShiftRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.service.WorkingWeek;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.entity.Department;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Shift master, assignments, rosters and the per-company shift/OT/late settings. */
@Service
public class ShiftService {

    public static final int MAX_ROSTER_DAYS = 62;
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{3,8}$");
    private static final BigDecimal MAX_DAY_HOURS = new BigDecimal("24");

    private final WorkShiftRepository shiftRepository;
    private final ShiftAssignmentRepository assignmentRepository;
    private final ShiftRosterRepository rosterRepository;
    private final AttendanceShiftSettingsRepository settingsRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    public ShiftService(WorkShiftRepository shiftRepository,
                        ShiftAssignmentRepository assignmentRepository,
                        ShiftRosterRepository rosterRepository,
                        AttendanceShiftSettingsRepository settingsRepository,
                        CompanyRepository companyRepository,
                        EmployeeRepository employeeRepository,
                        DepartmentRepository departmentRepository,
                        AuthorizationService authorizationService,
                        AuditLogService auditLogService) {
        this.shiftRepository = shiftRepository;
        this.assignmentRepository = assignmentRepository;
        this.rosterRepository = rosterRepository;
        this.settingsRepository = settingsRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
    }

    // ---------------------------------------------------------------- shift master

    /** Lists shifts, creating the "General shift 09:30-18:30" default on first use for new companies. */
    @Transactional
    public List<ShiftDTO> listShifts() {
        Long companyId = requiredTenant();
        ensureDefaultShift(companyId);
        return shiftRepository.findAllByCompany_IdAndDeletedFalseOrderByStartTimeAscNameAsc(companyId)
                .stream().map(ShiftDTO::from).toList();
    }

    /** Idempotent: returns the company default shift, creating the General shift when none exists. */
    @Transactional
    public WorkShift ensureDefaultShift(Long companyId) {
        Optional<WorkShift> existing = shiftRepository.findFirstByCompany_IdAndCompanyDefaultTrueAndDeletedFalse(companyId);
        if (existing.isPresent()) return existing.get();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found."));
        List<WorkShift> shifts = shiftRepository.findAllByCompany_IdAndDeletedFalseOrderByStartTimeAscNameAsc(companyId);
        if (!shifts.isEmpty()) {
            WorkShift first = shifts.get(0);
            first.setCompanyDefault(true);
            return shiftRepository.save(first);
        }
        ShiftRule general = ShiftRule.general(company.getAttendanceGraceMinutes());
        WorkShift shift = new WorkShift();
        shift.setCompany(company);
        shift.setName(general.name());
        shift.setCode(general.code());
        shift.setStartTime(general.start());
        shift.setEndTime(general.end());
        shift.setBreakMinutes(general.breakMinutes());
        shift.setGraceInMinutes(general.graceInMinutes());
        shift.setGraceOutMinutes(general.graceOutMinutes());
        shift.setHalfDayHours(general.halfDayHours());
        shift.setFullDayHours(general.fullDayHours());
        shift.setOvertimeEnabled(general.overtimeEnabled());
        shift.setMinOvertimeMinutes(general.minOvertimeMinutes());
        shift.setColor(general.color());
        shift.setCompanyDefault(true);
        return shiftRepository.save(shift);
    }

    @Transactional
    public ShiftDTO createShift(ShiftRequest request) {
        Long companyId = requiredTenant();
        String code = normalizeCode(request.code());
        if (shiftRepository.existsByCompany_IdAndCodeIgnoreCaseAndDeletedFalse(companyId, code)) {
            throw new BadRequestException("A shift with code " + code + " already exists.");
        }
        WorkShift shift = new WorkShift();
        shift.setCompany(company(companyId));
        apply(shift, request, code);
        boolean hasDefault = shiftRepository.findFirstByCompany_IdAndCompanyDefaultTrueAndDeletedFalse(companyId).isPresent();
        shift.setCompanyDefault(!hasDefault);
        WorkShift saved = shiftRepository.save(shift);
        auditLogService.log("WorkShift", saved.getId(), "CREATE", "Created shift " + describe(saved));
        return ShiftDTO.from(saved);
    }

    @Transactional
    public ShiftDTO updateShift(Long id, ShiftRequest request) {
        Long companyId = requiredTenant();
        WorkShift shift = shift(id, companyId);
        String code = normalizeCode(request.code());
        if (shiftRepository.existsByCompany_IdAndCodeIgnoreCaseAndDeletedFalseAndIdNot(companyId, code, id)) {
            throw new BadRequestException("A shift with code " + code + " already exists.");
        }
        String before = describe(shift);
        apply(shift, request, code);
        if (shift.isCompanyDefault() && !shift.isActive()) {
            throw new BadRequestException("The company default shift cannot be deactivated. Make another shift the default first.");
        }
        WorkShift saved = shiftRepository.save(shift);
        auditLogService.log("WorkShift", saved.getId(), "UPDATE", "Shift changed from [" + before + "] to [" + describe(saved) + "]");
        return ShiftDTO.from(saved);
    }

    @Transactional
    public ShiftDTO makeDefault(Long id) {
        Long companyId = requiredTenant();
        WorkShift shift = shift(id, companyId);
        if (!shift.isActive()) throw new BadRequestException("Activate the shift before making it the default.");
        shiftRepository.findAllByCompany_IdAndDeletedFalseOrderByStartTimeAscNameAsc(companyId).forEach(other -> {
            if (other.isCompanyDefault() && !other.getId().equals(id)) {
                other.setCompanyDefault(false);
                shiftRepository.save(other);
            }
        });
        shift.setCompanyDefault(true);
        WorkShift saved = shiftRepository.save(shift);
        auditLogService.log("WorkShift", saved.getId(), "DEFAULT", "Company default shift set to " + saved.getName());
        return ShiftDTO.from(saved);
    }

    @Transactional
    public void deleteShift(Long id) {
        Long companyId = requiredTenant();
        WorkShift shift = shift(id, companyId);
        if (shift.isCompanyDefault()) {
            throw new BadRequestException("The company default shift cannot be deleted. Make another shift the default first.");
        }
        if (assignmentRepository.existsByShift_IdAndDeletedFalse(id)) {
            throw new BadRequestException("This shift is assigned to departments or employees. Remove those assignments first.");
        }
        if (rosterRepository.existsByShift_IdAndDeletedFalseAndRosterDateGreaterThanEqual(id, LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")))) {
            throw new BadRequestException("This shift is used in upcoming rosters. Change those roster days first.");
        }
        shift.setDeleted(true);
        shift.setDeletedAt(LocalDateTime.now());
        shift.setActive(false);
        shiftRepository.save(shift);
        auditLogService.log("WorkShift", id, "DELETE", "Deleted shift " + shift.getName());
    }

    private void apply(WorkShift shift, ShiftRequest request, String code) {
        LocalTime start = ShiftTimes.parse(request.startTime(), "Start time");
        LocalTime end = ShiftTimes.parse(request.endTime(), "End time");
        if (start.equals(end)) throw new BadRequestException("Start and end time cannot be the same.");
        int breakMinutes = bounded(request.breakMinutes(), 0, 0, 480, "Break minutes");
        int graceIn = bounded(request.graceInMinutes(), 0, 0, 240, "Grace-in minutes");
        int graceOut = bounded(request.graceOutMinutes(), 0, 0, 240, "Grace-out minutes");
        int minOt = bounded(request.minOvertimeMinutes(), 30, 0, 720, "Minimum overtime minutes");
        BigDecimal half = request.halfDayHours() == null ? new BigDecimal("4") : request.halfDayHours();
        BigDecimal full = request.fullDayHours() == null ? new BigDecimal("8") : request.fullDayHours();
        if (half.signum() < 0 || full.signum() <= 0 || full.compareTo(MAX_DAY_HOURS) > 0) {
            throw new BadRequestException("Full-day hours must be between 0 and 24, and half-day hours cannot be negative.");
        }
        if (half.compareTo(full) > 0) throw new BadRequestException("Half-day hours cannot exceed full-day hours.");
        String color = request.color() == null || request.color().isBlank() ? null : request.color().trim();
        if (color != null && !COLOR.matcher(color).matches()) {
            throw new BadRequestException("Colour must be a hex value like #2563eb.");
        }
        shift.setName(request.name().trim());
        shift.setCode(code);
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setBreakMinutes(breakMinutes);
        shift.setGraceInMinutes(graceIn);
        shift.setGraceOutMinutes(graceOut);
        shift.setHalfDayHours(half);
        shift.setFullDayHours(full);
        shift.setOvertimeEnabled(Boolean.TRUE.equals(request.overtimeEnabled()));
        shift.setMinOvertimeMinutes(minOt);
        shift.setColor(color);
        shift.setActive(request.active() == null || request.active());
        if (ShiftRule.from(shift).spanMinutes() <= breakMinutes) {
            throw new BadRequestException("The break must be shorter than the shift.");
        }
    }

    // ---------------------------------------------------------------- assignments

    @Transactional(readOnly = true)
    public List<ShiftAssignmentDTO> listAssignments() {
        return assignmentRepository.findAllByCompany_IdAndDeletedFalseOrderByEffectiveFromDesc(requiredTenant())
                .stream().map(ShiftAssignmentDTO::from).toList();
    }

    @Transactional
    public ShiftAssignmentDTO saveAssignment(ShiftAssignmentRequest request) {
        Long companyId = requiredTenant();
        if ((request.departmentId() == null) == (request.employeeId() == null)) {
            throw new BadRequestException("Choose either a department or an employee for the assignment.");
        }
        WorkShift shift = shift(request.shiftId(), companyId);
        if (!shift.isActive()) throw new BadRequestException("Choose an active shift.");
        Department department = request.departmentId() == null ? null
                : departmentRepository.findByIdAndCompany_IdAndDeletedFalse(request.departmentId(), companyId)
                .orElseThrow(() -> new BadRequestException("Department not found in this company."));
        Employee employee = request.employeeId() == null ? null
                : employeeRepository.findByIdAndCompany_IdAndDeletedFalse(request.employeeId(), companyId)
                .orElseThrow(() -> new BadRequestException("Employee not found in this company."));
        ShiftAssignment assignment = assignmentRepository.findAllByCompany_IdAndDeletedFalseOrderByEffectiveFromDesc(companyId)
                .stream()
                .filter(a -> request.effectiveFrom().equals(a.getEffectiveFrom()))
                .filter(a -> department != null
                        ? a.getDepartment() != null && a.getDepartment().getId().equals(department.getId())
                        : a.getEmployee() != null && a.getEmployee().getId().equals(employee.getId()))
                .findFirst()
                .orElseGet(ShiftAssignment::new);
        assignment.setCompany(company(companyId));
        assignment.setShift(shift);
        assignment.setDepartment(department);
        assignment.setEmployee(employee);
        assignment.setEffectiveFrom(request.effectiveFrom());
        ShiftAssignment saved = assignmentRepository.save(assignment);
        auditLogService.log("ShiftAssignment", saved.getId(), "SAVE", "Shift " + shift.getName() + " assigned to "
                + (department != null ? "department " + department.getName() : "employee " + employee.getId())
                + " from " + saved.getEffectiveFrom());
        return ShiftAssignmentDTO.from(saved);
    }

    @Transactional
    public void deleteAssignment(Long id) {
        Long companyId = requiredTenant();
        ShiftAssignment assignment = assignmentRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift assignment not found."));
        assignment.setDeleted(true);
        assignment.setDeletedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
        auditLogService.log("ShiftAssignment", id, "DELETE", "Removed shift assignment " + id);
    }

    // ---------------------------------------------------------------- settings

    /** The company's settings row, or unsaved defaults when none exists yet. Works without a tenant context. */
    @Transactional(readOnly = true)
    public AttendanceShiftSettings settingsFor(Long companyId) {
        return settingsRepository.findByCompany_IdAndDeletedFalse(companyId).orElseGet(AttendanceShiftSettings::new);
    }

    @Transactional(readOnly = true)
    public ShiftSettingsDTO getSettings() {
        return toDto(settingsFor(requiredTenant()));
    }

    @Transactional
    public ShiftSettingsDTO updateSettings(ShiftSettingsDTO request) {
        Long companyId = requiredTenant();
        String type = request.overtimeRateType() == null ? AttendanceShiftSettings.RATE_MULTIPLIER
                : request.overtimeRateType().trim().toUpperCase(Locale.ROOT);
        if (!AttendanceShiftSettings.RATE_MULTIPLIER.equals(type) && !AttendanceShiftSettings.RATE_FLAT.equals(type)) {
            throw new BadRequestException("Overtime rate type must be MULTIPLIER or FLAT.");
        }
        BigDecimal multiplier = request.overtimeMultiplier() == null ? new BigDecimal("1.5") : request.overtimeMultiplier();
        if (multiplier.signum() <= 0 || multiplier.compareTo(new BigDecimal("5")) > 0) {
            throw new BadRequestException("Overtime multiplier must be between 0 and 5 (e.g. 1, 1.5 or 2).");
        }
        BigDecimal flat = request.overtimeFlatHourlyRate();
        if (AttendanceShiftSettings.RATE_FLAT.equals(type) && (flat == null || flat.signum() <= 0)) {
            throw new BadRequestException("Enter the flat overtime amount per hour.");
        }
        if (flat != null && flat.signum() < 0) throw new BadRequestException("Flat overtime rate cannot be negative.");
        if (request.lateMarksPerPenalty() < 1 || request.lateMarksPerPenalty() > 31) {
            throw new BadRequestException("Late marks per penalty must be between 1 and 31.");
        }
        BigDecimal penaltyDays = request.latePenaltyDays() == null ? new BigDecimal("0.5") : request.latePenaltyDays();
        if (penaltyDays.signum() <= 0 || penaltyDays.compareTo(new BigDecimal("5")) > 0) {
            throw new BadRequestException("Penalty days must be between 0 and 5.");
        }
        AttendanceShiftSettings settings = settingsRepository.findByCompany_IdAndDeletedFalse(companyId)
                .orElseGet(() -> {
                    AttendanceShiftSettings created = new AttendanceShiftSettings();
                    created.setCompany(company(companyId));
                    return created;
                });
        String before = describe(settings);
        settings.setOvertimeRateType(type);
        settings.setOvertimeMultiplier(multiplier);
        settings.setOvertimeFlatHourlyRate(flat);
        settings.setLatePenaltyEnabled(request.latePenaltyEnabled());
        settings.setLateMarksPerPenalty(request.lateMarksPerPenalty());
        settings.setLatePenaltyDays(penaltyDays);
        AttendanceShiftSettings saved = settingsRepository.save(settings);
        auditLogService.log("AttendanceShiftSettings", saved.getId(), "UPDATE",
                "Shift settings changed from [" + before + "] to [" + describe(saved) + "]");
        return toDto(saved);
    }

    private ShiftSettingsDTO toDto(AttendanceShiftSettings s) {
        return new ShiftSettingsDTO(s.getOvertimeRateType(), s.getOvertimeMultiplier(), s.getOvertimeFlatHourlyRate(),
                s.isLatePenaltyEnabled(), s.getLateMarksPerPenalty(), s.getLatePenaltyDays());
    }

    private String describe(AttendanceShiftSettings s) {
        return "otType=" + s.getOvertimeRateType() + ",multiplier=" + s.getOvertimeMultiplier()
                + ",flat=" + s.getOvertimeFlatHourlyRate() + ",latePenalty=" + s.isLatePenaltyEnabled()
                + ",perMarks=" + s.getLateMarksPerPenalty() + ",days=" + s.getLatePenaltyDays();
    }

    // ---------------------------------------------------------------- calendar (resolution)

    /**
     * Loads everything needed to resolve shifts and week-offs for [from, to] without a tenant context
     * (used by payroll hooks and scheduled jobs too). Does not write; a company without any shift
     * resolves to the built-in General shift.
     */
    @Transactional(readOnly = true)
    public ShiftCalendar loadCalendar(Long companyId, LocalDate from, LocalDate to) {
        Company company = companyRepository.findById(companyId).orElse(null);
        Map<Long, ShiftRule> rules = new HashMap<>();
        ShiftRule companyDefault = null;
        for (WorkShift shift : shiftRepository.findAllByCompany_IdAndDeletedFalseOrderByStartTimeAscNameAsc(companyId)) {
            ShiftRule rule = ShiftRule.from(shift);
            rules.put(shift.getId(), rule);
            if (shift.isCompanyDefault()) companyDefault = rule;
        }
        if (companyDefault == null) {
            companyDefault = ShiftRule.general(company == null ? 10 : company.getAttendanceGraceMinutes());
        }
        ShiftCalendar calendar = new ShiftCalendar(companyDefault, rules, WorkingWeek.of(company));
        for (ShiftAssignment assignment : assignmentRepository.findAllByCompany_IdAndDeletedFalseOrderByEffectiveFromDesc(companyId)) {
            if (assignment.getEmployee() != null) {
                calendar.addEmployeeAssignment(assignment.getEmployee().getId(), assignment.getShift().getId(), assignment.getEffectiveFrom());
            } else if (assignment.getDepartment() != null) {
                calendar.addDepartmentAssignment(assignment.getDepartment().getId(), assignment.getShift().getId(), assignment.getEffectiveFrom());
            }
        }
        for (ShiftRoster cell : rosterRepository.findAllByCompany_IdAndDeletedFalseAndRosterDateBetween(companyId, from, to)) {
            calendar.addRosterCell(cell.getEmployee().getId(), cell.getRosterDate(),
                    cell.getShift() == null ? null : cell.getShift().getId(), cell.isWeekOff());
        }
        return calendar;
    }

    // ---------------------------------------------------------------- roster

    @Transactional(readOnly = true)
    public RosterDTO roster(LocalDate from, LocalDate to, Long departmentId) {
        Long companyId = requiredTenant();
        validateRange(from, to);
        List<Employee> employees = scopedEmployees(companyId, from, to).stream()
                .filter(e -> departmentId == null || (e.getDepartment() != null && departmentId.equals(e.getDepartment().getId())))
                .toList();
        ShiftCalendar calendar = loadCalendar(companyId, from, to);
        List<ShiftDTO> shifts = shiftRepository.findAllByCompany_IdAndDeletedFalseOrderByStartTimeAscNameAsc(companyId)
                .stream().map(ShiftDTO::from).toList();
        List<RosterDTO.Row> rows = new ArrayList<>();
        for (Employee employee : employees) {
            Long deptId = employee.getDepartment() == null ? null : employee.getDepartment().getId();
            List<RosterDTO.Cell> cells = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                ShiftCalendar.Resolved resolved = calendar.resolve(employee.getId(), deptId, d);
                ShiftRule rule = resolved.shift();
                cells.add(new RosterDTO.Cell(d, rule.shiftId(), rule.code(), rule.color(), resolved.weekOff(),
                        resolved.rostered(), resolved.source()));
            }
            rows.add(new RosterDTO.Row(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                    employee.getDepartment() == null ? null : employee.getDepartment().getName(), cells));
        }
        return new RosterDTO(from, to, shifts, rows);
    }

    @Transactional
    public int updateRoster(RosterUpdateRequest request) {
        Long companyId = requiredTenant();
        Company company = company(companyId);
        Map<Long, WorkShift> shifts = new HashMap<>();
        Map<Long, Employee> employees = new HashMap<>();
        int changed = 0;
        for (RosterUpdateRequest.CellUpdate update : request.cells()) {
            Employee employee = employees.computeIfAbsent(update.employeeId(), id -> rosterEmployee(id, companyId));
            WorkShift shift = null;
            if (!update.clear() && update.shiftId() != null) {
                shift = shifts.computeIfAbsent(update.shiftId(), id -> shift(id, companyId));
                if (!shift.isActive()) throw new BadRequestException("Shift " + shift.getName() + " is inactive.");
            }
            if (!update.clear() && !update.weekOff() && shift == null) {
                throw new BadRequestException("Choose a shift or week off for " + employee.getFullName() + " on " + update.date() + ".");
            }
            changed += writeCell(company, employee, update.date(), update.clear(), shift, update.weekOff()) ? 1 : 0;
        }
        if (changed > 0) {
            auditLogService.log("ShiftRoster", null, "UPDATE", "Updated " + changed + " roster cell(s)");
        }
        return changed;
    }

    @Transactional
    public int copyWeek(RosterCopyWeekRequest request) {
        Long companyId = requiredTenant();
        Company company = company(companyId);
        int repeat = request.repeatWeeks() == null ? 1 : request.repeatWeeks();
        if (repeat < 1 || repeat > 8) throw new BadRequestException("Repeat between 1 and 8 weeks.");
        LocalDate source = request.sourceWeekStart();
        LocalDate target = request.targetWeekStart();
        long gap = ChronoUnit.DAYS.between(source, target);
        if (gap > -7 && gap < 7) throw new BadRequestException("The target week must not overlap the source week.");

        Collection<Long> employeeIds;
        if (request.employeeIds() != null && !request.employeeIds().isEmpty()) {
            employeeIds = new HashSet<>(request.employeeIds());
        } else {
            employeeIds = scopedEmployees(companyId, source, target.plusDays(7L * repeat - 1)).stream().map(Employee::getId).toList();
        }
        if (employeeIds.isEmpty()) return 0;
        Map<Long, Map<LocalDate, ShiftRoster>> sourceCells = new HashMap<>();
        for (ShiftRoster cell : rosterRepository.findAllByCompany_IdAndEmployee_IdInAndDeletedFalseAndRosterDateBetween(
                companyId, employeeIds, source, source.plusDays(6))) {
            sourceCells.computeIfAbsent(cell.getEmployee().getId(), k -> new HashMap<>()).put(cell.getRosterDate(), cell);
        }
        int changed = 0;
        for (Long employeeId : employeeIds) {
            Employee employee = rosterEmployee(employeeId, companyId);
            Map<LocalDate, ShiftRoster> cells = sourceCells.getOrDefault(employeeId, Map.of());
            for (int week = 0; week < repeat; week++) {
                for (int day = 0; day < 7; day++) {
                    ShiftRoster from = cells.get(source.plusDays(day));
                    LocalDate date = target.plusDays(7L * week + day);
                    boolean clear = from == null;
                    changed += writeCell(company, employee, date, clear,
                            clear ? null : from.getShift(), !clear && from.isWeekOff()) ? 1 : 0;
                }
            }
        }
        auditLogService.log("ShiftRoster", null, "COPY_WEEK", "Copied roster week " + source + " to " + target
                + " x" + repeat + " (" + changed + " cell(s))");
        return changed;
    }

    private boolean writeCell(Company company, Employee employee, LocalDate date, boolean clear, WorkShift shift, boolean weekOff) {
        Optional<ShiftRoster> existing = rosterRepository.findByEmployee_IdAndRosterDate(employee.getId(), date);
        if (clear) {
            if (existing.isEmpty() || existing.get().isDeleted()) return false;
            ShiftRoster row = existing.get();
            row.setDeleted(true);
            row.setDeletedAt(LocalDateTime.now());
            rosterRepository.save(row);
            return true;
        }
        ShiftRoster row = existing.orElseGet(ShiftRoster::new);
        row.setCompany(company);
        row.setEmployee(employee);
        row.setRosterDate(date);
        row.setShift(shift);
        row.setWeekOff(weekOff);
        row.setDeleted(false);
        row.setDeletedAt(null);
        rosterRepository.save(row);
        return true;
    }

    private Employee rosterEmployee(Long employeeId, Long companyId) {
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new BadRequestException("Employee not found in this company."));
        if (!authorizationService.isAllowed("ATTENDANCE_MANAGE", "EMPLOYEE", employeeId)) {
            throw new AccessDeniedException("You are not allowed to change the roster for " + employee.getFullName() + ".");
        }
        return employee;
    }

    /** Employees visible under ATTENDANCE_VIEW who are employed at some point in [from, to]. */
    @Transactional(readOnly = true)
    public List<Employee> scopedEmployees(Long companyId, LocalDate from, LocalDate to) {
        Optional<Set<Long>> scope = authorizationService.resolveEmployeeIds("ATTENDANCE_VIEW");
        List<Employee> employees;
        if (scope.isPresent() && scope.get().isEmpty()) {
            employees = List.of();
        } else if (scope.isPresent()) {
            employees = employeeRepository.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, scope.get());
        } else {
            employees = employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId);
        }
        return employees.stream()
                .filter(e -> e.getDateOfJoining() == null || !e.getDateOfJoining().isAfter(to))
                .filter(e -> e.getLastWorkingDate() == null || !e.getLastWorkingDate().isBefore(from))
                .toList();
    }

    public static void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new BadRequestException("Choose a from and to date.");
        if (to.isBefore(from)) throw new BadRequestException("The end date must be on or after the start date.");
        if (ChronoUnit.DAYS.between(from, to) >= MAX_ROSTER_DAYS) {
            throw new BadRequestException("Choose a range of at most " + MAX_ROSTER_DAYS + " days.");
        }
    }

    // ---------------------------------------------------------------- helpers

    private WorkShift shift(Long id, Long companyId) {
        return shiftRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Shift not found."));
    }

    private Company company(Long companyId) {
        return companyRepository.findById(companyId).orElseThrow(() -> new BadRequestException("Company not found."));
    }

    private String normalizeCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new BadRequestException("Shift code is required.");
        return normalized;
    }

    private static int bounded(Integer value, int fallback, int min, int max, String label) {
        int v = value == null ? fallback : value;
        if (v < min || v > max) throw new BadRequestException(label + " must be between " + min + " and " + max + ".");
        return v;
    }

    private String describe(WorkShift s) {
        return s.getCode() + " " + s.getName() + " " + s.getStartTime() + "-" + s.getEndTime()
                + ",break=" + s.getBreakMinutes() + ",graceIn=" + s.getGraceInMinutes() + ",graceOut=" + s.getGraceOutMinutes()
                + ",half=" + s.getHalfDayHours() + ",full=" + s.getFullDayHours() + ",ot=" + s.isOvertimeEnabled()
                + "/" + s.getMinOvertimeMinutes() + ",active=" + s.isActive();
    }

    private Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required.");
        return companyId;
    }
}
