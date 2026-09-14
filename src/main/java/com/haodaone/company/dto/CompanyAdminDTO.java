package com.haodaone.company.dto;

import com.haodaone.company.entity.Company;
import jakarta.validation.constraints.NotBlank;

public record CompanyAdminDTO(Long id, String name, String domain, Integer productiveThresholdPercent, Integer neutralThresholdPercent) {
    public static CompanyAdminDTO from(Company company) { return new CompanyAdminDTO(company.getId(), company.getName(), company.getDomain(), company.getProductiveThresholdPercent(), company.getNeutralThresholdPercent()); }
    public record WriteRequest(@NotBlank String name, String domain, Integer productiveThresholdPercent, Integer neutralThresholdPercent) { }
}