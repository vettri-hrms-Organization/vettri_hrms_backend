package com.haodaone.leave.dto;

import java.time.LocalDate;
import java.util.List;

/** The holidays that apply to one employee in a year, with their optional-holiday picks. */
public record EmployeeHolidaysDTO(Long employeeId, String employeeName, int year, Long officeLocationId,
                                  String officeLocationName, int maxOptionalHolidays, int optionalPicked,
                                  List<Row> holidays) {

    /**
     * @param counts    true when the day is a holiday for this employee (mandatory, or optional and picked)
     * @param canPick   optional, not picked, still in the future and quota left
     * @param canUnpick optional, picked and still in the future
     */
    public record Row(Long id, String name, LocalDate date, String holidayType, List<String> locationNames,
                      boolean picked, boolean counts, boolean canPick, boolean canUnpick) {
    }

    public record SettingsDTO(int maxOptionalHolidays) {
    }
}
