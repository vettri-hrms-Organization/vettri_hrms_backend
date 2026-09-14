package com.haodaone.company.dto;

import com.haodaone.company.entity.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SubscriptionAdminDTO(Long id, Long companyId, Plan plan, SubscriptionStatus status, Integer employeeLimit,
                                   Integer deviceLimit, LocalDate startDate, LocalDate renewalDate, BigDecimal amount) {
    public static SubscriptionAdminDTO from(Subscription s) { return new SubscriptionAdminDTO(s.getId(), s.getCompany().getId(), s.getPlan(), s.getStatus(), s.getEmployeeLimit(), s.getDeviceLimit(), s.getStartDate(), s.getRenewalDate(), s.getAmount()); }
    public record WriteRequest(@NotNull Plan plan, @NotNull SubscriptionStatus status, Integer employeeLimit, Integer deviceLimit,
                               LocalDate startDate, LocalDate renewalDate, BigDecimal amount) { }
}