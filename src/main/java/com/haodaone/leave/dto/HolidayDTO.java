package com.haodaone.leave.dto;

import com.haodaone.leave.entity.Holiday;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class HolidayDTO {
    private Long id;
    private String name;
    private LocalDate date;

    public static HolidayDTO from(Holiday h) {
        HolidayDTO dto = new HolidayDTO();
        dto.id = h.getId();
        dto.name = h.getName();
        dto.date = h.getDate();
        return dto;
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
