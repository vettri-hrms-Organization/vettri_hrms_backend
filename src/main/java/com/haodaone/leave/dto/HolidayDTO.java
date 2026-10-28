package com.haodaone.leave.dto;

import com.haodaone.leave.entity.Holiday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class HolidayDTO {
    private Long id;
    private String name;
    private LocalDate date;
    private String holidayType;
    private java.util.List<Long> locationIds = java.util.List.of();
    private java.util.List<String> locationNames = java.util.List.of();

    public static HolidayDTO from(Holiday h) {
        HolidayDTO dto = new HolidayDTO();
        dto.id = h.getId();
        dto.name = h.getName();
        dto.date = h.getDate();
        dto.holidayType = h.getHolidayType();
        dto.locationIds = h.getLocationIds() == null ? java.util.List.of() : h.getLocationIds().stream().sorted().toList();
        return dto;
    }

    /** Same as {@link #from(Holiday)} plus display names for the holiday's locations (id -> name). */
    public static HolidayDTO from(Holiday h, java.util.Map<Long, String> locationNamesById) {
        HolidayDTO dto = from(h);
        dto.locationNames = dto.locationIds.stream()
                .map(id -> locationNamesById.getOrDefault(id, "Location #" + id)).toList();
        return dto;
    }

    public String getHolidayType() {
        return holidayType;
    }

    public java.util.List<Long> getLocationIds() {
        return locationIds;
    }

    public java.util.List<String> getLocationNames() {
        return locationNames;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public LocalDate getDate() {
        return date;
    }

    public static class CreateRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotNull(message = "Date is required")
        private LocalDate date;

        /** MANDATORY (default) or OPTIONAL. */
        private String holidayType;

        /** Office locations it applies to; empty/null = all locations. */
        private java.util.List<Long> locationIds;

        public String getHolidayType() {
            return holidayType;
        }

        public void setHolidayType(String holidayType) {
            this.holidayType = holidayType;
        }

        public java.util.List<Long> getLocationIds() {
            return locationIds;
        }

        public void setLocationIds(java.util.List<Long> locationIds) {
            this.locationIds = locationIds;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }
    }
}
