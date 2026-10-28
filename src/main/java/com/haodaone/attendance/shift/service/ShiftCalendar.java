package com.haodaone.attendance.shift.service;

import com.haodaone.company.service.WorkingWeek;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory shift resolution for one company over a date range. Pure (no Spring) so the
 * resolution order is unit-testable:
 * roster entry for the date > employee assignment > department assignment > company default.
 * Assignments apply from their effective-from date until a later assignment for the same target.
 * Week off: an explicit roster entry decides (week-off cell = off, shift cell = working day);
 * otherwise the company's working week (weekly offs incl. SATURDAY#2 style) decides.
 */
public final class ShiftCalendar {

    public static final String SOURCE_ROSTER = "ROSTER";
    public static final String SOURCE_EMPLOYEE = "EMPLOYEE";
    public static final String SOURCE_DEPARTMENT = "DEPARTMENT";
    public static final String SOURCE_COMPANY = "COMPANY";

    public record Assignment(Long shiftId, LocalDate effectiveFrom) {}

    public record RosterCell(Long shiftId, boolean weekOff) {}

    public record Resolved(ShiftRule shift, String source, boolean weekOff, boolean rostered) {}

    private final ShiftRule companyDefault;
    private final Map<Long, ShiftRule> shiftsById;
    private final Map<Long, List<Assignment>> employeeAssignments = new HashMap<>();
    private final Map<Long, List<Assignment>> departmentAssignments = new HashMap<>();
    private final Map<Long, Map<LocalDate, RosterCell>> roster = new HashMap<>();
    private final WorkingWeek workingWeek;

    public ShiftCalendar(ShiftRule companyDefault, Map<Long, ShiftRule> shiftsById, WorkingWeek workingWeek) {
        this.companyDefault = companyDefault == null ? ShiftRule.general(10) : companyDefault;
        this.shiftsById = shiftsById == null ? Map.of() : shiftsById;
        this.workingWeek = workingWeek == null ? WorkingWeek.defaultWeek() : workingWeek;
    }

    public void addEmployeeAssignment(Long employeeId, Long shiftId, LocalDate effectiveFrom) {
        add(employeeAssignments, employeeId, new Assignment(shiftId, effectiveFrom));
    }

    public void addDepartmentAssignment(Long departmentId, Long shiftId, LocalDate effectiveFrom) {
        add(departmentAssignments, departmentId, new Assignment(shiftId, effectiveFrom));
    }

    public void addRosterCell(Long employeeId, LocalDate date, Long shiftId, boolean weekOff) {
        roster.computeIfAbsent(employeeId, k -> new HashMap<>()).put(date, new RosterCell(shiftId, weekOff));
    }

    public ShiftRule companyDefault() {
        return companyDefault;
    }

    public Map<Long, ShiftRule> shifts() {
        return shiftsById;
    }

    public RosterCell rosterCell(Long employeeId, LocalDate date) {
        Map<LocalDate, RosterCell> cells = roster.get(employeeId);
        return cells == null ? null : cells.get(date);
    }

    public Resolved resolve(Long employeeId, Long departmentId, LocalDate date) {
        RosterCell cell = rosterCell(employeeId, date);
        boolean weekOff = cell != null ? cell.weekOff() : workingWeek.isWeeklyOff(date);
        if (cell != null && cell.shiftId() != null && shiftsById.containsKey(cell.shiftId())) {
            return new Resolved(shiftsById.get(cell.shiftId()), SOURCE_ROSTER, weekOff, true);
        }
        ShiftRule fromEmployee = effective(employeeAssignments.get(employeeId), date);
        if (fromEmployee != null) return new Resolved(fromEmployee, SOURCE_EMPLOYEE, weekOff, cell != null);
        ShiftRule fromDepartment = departmentId == null ? null : effective(departmentAssignments.get(departmentId), date);
        if (fromDepartment != null) return new Resolved(fromDepartment, SOURCE_DEPARTMENT, weekOff, cell != null);
        return new Resolved(companyDefault, SOURCE_COMPANY, weekOff, cell != null);
    }

    private ShiftRule effective(List<Assignment> assignments, LocalDate date) {
        if (assignments == null) return null;
        for (Assignment assignment : assignments) { // sorted newest effective-from first
            if (assignment.effectiveFrom() != null && assignment.effectiveFrom().isAfter(date)) continue;
            ShiftRule rule = shiftsById.get(assignment.shiftId());
            if (rule != null) return rule;
        }
        return null;
    }

    private static void add(Map<Long, List<Assignment>> target, Long key, Assignment assignment) {
        if (key == null || assignment.shiftId() == null) return;
        List<Assignment> list = target.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(assignment);
        list.sort(Comparator.comparing(Assignment::effectiveFrom, Comparator.nullsLast(Comparator.reverseOrder())));
    }
}
