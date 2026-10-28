package com.haodaone.leave.service;

import com.haodaone.employee.entity.Employee;
import com.haodaone.leave.entity.Holiday;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.OptionalHolidaySelectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Employee-aware holiday calendar. A holiday counts for an employee when:
 *  - it applies to the employee's work location (a holiday with no locations applies to everyone;
 *    an employee with no location only gets holidays with no locations), and
 *  - it is MANDATORY, or it is OPTIONAL and the employee picked it.
 * Used by leave-day counting, comp-off and the attendance muster.
 */
@Service
public class HolidayCalendarService {

    private final HolidayRepository holidayRepository;
    private final OptionalHolidaySelectionRepository selectionRepository;

    public HolidayCalendarService(HolidayRepository holidayRepository, OptionalHolidaySelectionRepository selectionRepository) {
        this.holidayRepository = holidayRepository;
        this.selectionRepository = selectionRepository;
    }

    /** Pure rule: does {@code holiday} count as a day off for an employee at {@code officeLocationId}? */
    public static boolean countsFor(Holiday holiday, Long officeLocationId, Set<Long> pickedHolidayIds) {
        if (holiday == null || holiday.isDeleted() || holiday.getDate() == null) return false;
        if (!holiday.appliesToLocation(officeLocationId)) return false;
        if (holiday.isOptional()) return pickedHolidayIds != null && holiday.getId() != null && pickedHolidayIds.contains(holiday.getId());
        return true;
    }

    /** Company-wide MANDATORY holidays only (for callers without an employee). */
    @Transactional(readOnly = true)
    public Set<LocalDate> companyHolidayDates(Long companyId, LocalDate from, LocalDate to) {
        Set<LocalDate> dates = new HashSet<>();
        for (Holiday h : holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, from, to)) {
            if (countsFor(h, null, Set.of())) dates.add(h.getDate());
        }
        return dates;
    }

    /** Holiday dates that apply to {@code employee} in [from, to]; company-wide mandatory ones when employee is null. */
    @Transactional(readOnly = true)
    public Set<LocalDate> holidayDatesFor(Long companyId, Employee employee, LocalDate from, LocalDate to) {
        if (employee == null || employee.getId() == null) return companyHolidayDates(companyId, from, to);
        return holidayDatesByEmployee(companyId, List.of(employee), from, to).getOrDefault(employee.getId(), Set.of());
    }

    /** Whether {@code date} is a holiday for {@code employee}. */
    @Transactional(readOnly = true)
    public boolean isHolidayFor(Long companyId, Employee employee, LocalDate date) {
        return holidayDatesFor(companyId, employee, date, date).contains(date);
    }

    /** Per-employee holiday dates in [from, to] with one holiday query and one selection query. */
    @Transactional(readOnly = true)
    public Map<Long, Set<LocalDate>> holidayDatesByEmployee(Long companyId, Collection<Employee> employees, LocalDate from, LocalDate to) {
        Map<Long, Set<LocalDate>> result = new HashMap<>();
        if (employees == null || employees.isEmpty()) return result;
        List<Holiday> holidays = holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, from, to);
        Set<Long> optionalIds = new HashSet<>();
        for (Holiday h : holidays) {
            if (h.isOptional() && h.getId() != null) optionalIds.add(h.getId());
        }
        Set<Long> employeeIds = new HashSet<>();
        for (Employee e : employees) {
            if (e != null && e.getId() != null) employeeIds.add(e.getId());
        }
        Map<Long, Set<Long>> picked = new HashMap<>();
        if (!optionalIds.isEmpty() && !employeeIds.isEmpty()) {
            for (Object[] row : selectionRepository.findPickedPairs(companyId, employeeIds, optionalIds)) {
                picked.computeIfAbsent((Long) row[0], k -> new HashSet<>()).add((Long) row[1]);
            }
        }
        for (Employee e : employees) {
            if (e == null || e.getId() == null) continue;
            Set<Long> mine = picked.getOrDefault(e.getId(), Set.of());
            Set<LocalDate> dates = new HashSet<>();
            for (Holiday h : holidays) {
                if (countsFor(h, e.getOfficeLocationId(), mine)) dates.add(h.getDate());
            }
            result.put(e.getId(), dates);
        }
        return result;
    }
}
