package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.EmployeeHolidaysDTO;
import com.haodaone.leave.entity.Holiday;
import com.haodaone.leave.entity.HolidaySettings;
import com.haodaone.leave.entity.OptionalHolidaySelection;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.HolidaySettingsRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.leave.repository.OptionalHolidaySelectionRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Optional (restricted) holidays: company quota per calendar year and employee self-service
 * picking. Rules: the holiday must be OPTIONAL and apply to the employee's location; it can be
 * picked or un-picked only before its date (strictly after today, IST); picks per year are capped
 * at {@link HolidaySettings#getMaxOptionalHolidays()}; a pick is refused when the employee already
 * has pending/approved leave on that date (cancel the leave first, so balances stay right).
 */
@Service
public class OptionalHolidayService {

    private final HolidayRepository holidayRepository;
    private final HolidaySettingsRepository settingsRepository;
    private final OptionalHolidaySelectionRepository selectionRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final CompanyRepository companyRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final HolidayService holidayService;

    public OptionalHolidayService(HolidayRepository holidayRepository, HolidaySettingsRepository settingsRepository,
                                  OptionalHolidaySelectionRepository selectionRepository, EmployeeRepository employeeRepository,
                                  LeaveRequestRepository leaveRequestRepository, CompanyRepository companyRepository,
                                  AuthorizationService authorizationService, AuditLogService auditLogService,
                                  HolidayService holidayService) {
        this.holidayRepository = holidayRepository;
        this.settingsRepository = settingsRepository;
        this.selectionRepository = selectionRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.companyRepository = companyRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.holidayService = holidayService;
    }

    // ------------------------------------------------------------------ settings

    @Transactional(readOnly = true)
    public EmployeeHolidaysDTO.SettingsDTO getSettings() {
        return new EmployeeHolidaysDTO.SettingsDTO(maxOptional(requiredTenant()));
    }

    @Transactional
    public EmployeeHolidaysDTO.SettingsDTO updateSettings(EmployeeHolidaysDTO.SettingsDTO request) {
        Long companyId = requiredTenant();
        int max = request == null ? HolidaySettings.DEFAULT_MAX_OPTIONAL : request.maxOptionalHolidays();
        if (max < 0 || max > 50) throw new BadRequestException("Optional holidays per year must be between 0 and 50");
        HolidaySettings settings = settingsRepository.findByCompany_IdAndDeletedFalse(companyId).orElseGet(() -> {
            HolidaySettings created = new HolidaySettings();
            created.setCompany(companyRepository.findById(companyId).orElseThrow(() -> new BadRequestException("Company not found")));
            return created;
        });
        settings.setMaxOptionalHolidays(max);
        HolidaySettings saved = settingsRepository.save(settings);
        auditLogService.log("HolidaySettings", saved.getId(), "UPDATE", "Optional holidays per year set to " + max);
        return new EmployeeHolidaysDTO.SettingsDTO(saved.getMaxOptionalHolidays());
    }

    public int maxOptional(Long companyId) {
        return settingsRepository.findByCompany_IdAndDeletedFalse(companyId)
                .map(HolidaySettings::getMaxOptionalHolidays).orElse(HolidaySettings.DEFAULT_MAX_OPTIONAL);
    }

    // ------------------------------------------------------------------ employee view

    @Transactional(readOnly = true)
    public EmployeeHolidaysDTO mine(Integer year) {
        return forEmployee(currentEmployee().getId(), year);
    }

    @Transactional(readOnly = true)
    public EmployeeHolidaysDTO forEmployee(Long employeeId, Integer year) {
        Long companyId = requiredTenant();
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        LocalDate today = LeaveBalanceService.today();
        int y = year != null ? year : today.getYear();
        Map<Long, String> locationNames = holidayService.locationNames(companyId);
        List<Holiday> holidays = holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(
                companyId, LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31));
        Set<Long> picked = pickedIds(companyId, employee.getId(), y);
        int max = maxOptional(companyId);
        int pickedCount = picked.size();
        List<Holiday> sorted = new ArrayList<>(holidays);
        sorted.sort(Comparator.comparing(Holiday::getDate));
        List<EmployeeHolidaysDTO.Row> rows = new ArrayList<>();
        for (Holiday h : sorted) {
            if (!h.appliesToLocation(employee.getOfficeLocationId())) continue;
            boolean isPicked = picked.contains(h.getId());
            boolean future = h.getDate().isAfter(today);
            boolean counts = HolidayCalendarService.countsFor(h, employee.getOfficeLocationId(), picked);
            List<String> names = h.getLocationIds().stream().sorted()
                    .map(id -> locationNames.getOrDefault(id, "Location #" + id)).toList();
            rows.add(new EmployeeHolidaysDTO.Row(h.getId(), h.getName(), h.getDate(), h.getHolidayType(), names,
                    isPicked, counts,
                    h.isOptional() && !isPicked && future && pickedCount < max,
                    h.isOptional() && isPicked && future));
        }
        Long locationId = employee.getOfficeLocationId();
        return new EmployeeHolidaysDTO(employee.getId(), employee.getFullName(), y, locationId,
                locationId == null ? null : locationNames.get(locationId), max, pickedCount, rows);
    }

    // ------------------------------------------------------------------ pick / unpick

    @Transactional
    public EmployeeHolidaysDTO pick(Long holidayId) {
        Long companyId = requiredTenant();
        Employee employee = currentEmployee();
        Holiday holiday = holidayRepository.findByIdAndCompany_IdAndDeletedFalse(holidayId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));
        int year = holiday.getDate().getYear();
        int used = pickedIds(companyId, employee.getId(), year).size();
        boolean alreadyPicked = selectionRepository
                .findFirstByCompany_IdAndEmployee_IdAndHoliday_IdAndDeletedFalse(companyId, employee.getId(), holidayId).isPresent();
        boolean hasLeave = !leaveRequestRepository.findOverlapping(employee.getId(), holiday.getDate(), holiday.getDate()).isEmpty();
        validatePick(holiday, employee.getOfficeLocationId(), LeaveBalanceService.today(), alreadyPicked, used, maxOptional(companyId), hasLeave);

        OptionalHolidaySelection selection = new OptionalHolidaySelection();
        selection.setCompany(companyRepository.findById(companyId).orElseThrow(() -> new BadRequestException("Company not found")));
        selection.setEmployee(employee);
        selection.setHoliday(holiday);
        selection.setHolidayYear(year);
        OptionalHolidaySelection saved = selectionRepository.save(selection);
        auditLogService.log("OptionalHolidaySelection", saved.getId(), "PICK",
                employee.getFullName() + " picked optional holiday '" + holiday.getName() + "' on " + holiday.getDate());
        return forEmployee(employee.getId(), year);
    }

    @Transactional
    public EmployeeHolidaysDTO unpick(Long holidayId) {
        Long companyId = requiredTenant();
        Employee employee = currentEmployee();
        OptionalHolidaySelection selection = selectionRepository
                .findFirstByCompany_IdAndEmployee_IdAndHoliday_IdAndDeletedFalse(companyId, employee.getId(), holidayId)
                .orElseThrow(() -> new BadRequestException("You have not picked this holiday"));
        Holiday holiday = selection.getHoliday();
        if (!holiday.getDate().isAfter(LeaveBalanceService.today())) {
            throw new BadRequestException("An optional holiday can only be dropped before its date");
        }
        selection.setDeleted(true);
        selection.setDeletedAt(java.time.LocalDateTime.now(LeaveBalanceService.IST));
        selectionRepository.save(selection);
        auditLogService.log("OptionalHolidaySelection", selection.getId(), "UNPICK",
                employee.getFullName() + " dropped optional holiday '" + holiday.getName() + "' on " + holiday.getDate());
        return forEmployee(employee.getId(), holiday.getDate().getYear());
    }

    /** Pure pick rules (unit-tested). Throws BadRequestException with a readable reason. */
    static void validatePick(Holiday holiday, Long employeeLocationId, LocalDate today, boolean alreadyPicked,
                             int usedThisYear, int maxPerYear, boolean hasLeaveThatDay) {
        if (!holiday.isOptional()) throw new BadRequestException("'" + holiday.getName() + "' is not an optional holiday");
        if (!holiday.appliesToLocation(employeeLocationId)) {
            throw new BadRequestException("'" + holiday.getName() + "' does not apply to your work location");
        }
        if (!holiday.getDate().isAfter(today)) throw new BadRequestException("Optional holidays can only be picked before their date");
        if (alreadyPicked) throw new BadRequestException("You have already picked '" + holiday.getName() + "'");
        if (usedThisYear >= maxPerYear) {
            throw new BadRequestException("You have used all " + maxPerYear + " optional holiday(s) for " + holiday.getDate().getYear()
                    + ". Drop one to pick another.");
        }
        if (hasLeaveThatDay) {
            throw new BadRequestException("You already have leave applied on " + holiday.getDate() + ". Cancel it before picking this holiday.");
        }
    }

    /** Ids of non-deleted optional holidays the employee picked in {@code year}. */
    private Set<Long> pickedIds(Long companyId, Long employeeId, int year) {
        Set<Long> ids = new HashSet<>();
        for (OptionalHolidaySelection s : selectionRepository.findAllByCompany_IdAndEmployee_IdAndHolidayYearAndDeletedFalse(companyId, employeeId, year)) {
            if (s.getHoliday() != null && !s.getHoliday().isDeleted() && s.getHoliday().isOptional()) ids.add(s.getHoliday().getId());
        }
        return ids;
    }

    private Employee currentEmployee() {
        Employee employee = authorizationService.resolveCurrentEmployee();
        if (employee == null) throw new BadRequestException("No employee profile is linked to your account");
        return employee;
    }

    private static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
