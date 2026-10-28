package com.haodaone.salary.dto;

import com.haodaone.salary.entity.StatutorySettings;
import com.haodaone.salary.statutory.ProfessionalTaxSlabs;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Company statutory payroll settings - request body for PUT and response for
 * GET /api/salary/statutory-settings. Response-only fields: {@code configured},
 * {@code resolvedPtState}, {@code ptStates}.
 */
public class StatutorySettingsDTO {

    private boolean pfEnabled = true;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal pfEmployeeRate = new BigDecimal("12.00");
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal pfEmployerRate = new BigDecimal("12.00");
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal epsRate = new BigDecimal("8.33");
    @NotNull @DecimalMin("0") private BigDecimal pfWageCeiling = new BigDecimal("15000.00");
    private boolean pfOnFullBasic;
    private boolean esiEnabled = true;
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal esiEmployeeRate = new BigDecimal("0.75");
    @NotNull @DecimalMin("0") @DecimalMax("100") private BigDecimal esiEmployerRate = new BigDecimal("3.25");
    @NotNull @DecimalMin("0") private BigDecimal esiWageThreshold = new BigDecimal("21000.00");
    private boolean ptEnabled = true;
    private String ptState;
    private boolean ptFixedOverride;
    @DecimalMin("0") private BigDecimal ptFixedAmount = BigDecimal.ZERO;
    private boolean tdsEnabled = true;

    private boolean configured;
    private String resolvedPtState;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private List<PtStateOption> ptStates = List.of();

    /** A selectable PT state with its slab summary for the settings screen. */
    public record PtStateOption(String code, String name, String basis, String effectiveFrom, String notes) {
        public static PtStateOption of(ProfessionalTaxSlabs.StateTable t) {
            return new PtStateOption(t.code(), t.name(), t.basis().name(), t.effectiveFrom(), t.notes());
        }
    }

    public static StatutorySettingsDTO from(StatutorySettings s, boolean configured, String resolvedPtState) {
        StatutorySettingsDTO dto = new StatutorySettingsDTO();
        dto.pfEnabled = s.isPfEnabled();
        dto.pfEmployeeRate = s.getPfEmployeeRate();
        dto.pfEmployerRate = s.getPfEmployerRate();
        dto.epsRate = s.getEpsRate();
        dto.pfWageCeiling = s.getPfWageCeiling();
        dto.pfOnFullBasic = s.isPfOnFullBasic();
        dto.esiEnabled = s.isEsiEnabled();
        dto.esiEmployeeRate = s.getEsiEmployeeRate();
        dto.esiEmployerRate = s.getEsiEmployerRate();
        dto.esiWageThreshold = s.getEsiWageThreshold();
        dto.ptEnabled = s.isPtEnabled();
        dto.ptState = s.getPtState();
        dto.ptFixedOverride = s.isPtFixedOverride();
        dto.ptFixedAmount = s.getPtFixedAmount();
        dto.tdsEnabled = s.isTdsEnabled();
        dto.configured = configured;
        dto.resolvedPtState = resolvedPtState;
        dto.ptStates = ProfessionalTaxSlabs.all().stream().map(PtStateOption::of).toList();
        return dto;
    }

    /** Copies the editable fields onto the entity. */
    public void applyTo(StatutorySettings s) {
        s.setPfEnabled(pfEnabled);
        s.setPfEmployeeRate(pfEmployeeRate);
        s.setPfEmployerRate(pfEmployerRate);
        s.setEpsRate(epsRate);
        s.setPfWageCeiling(pfWageCeiling);
        s.setPfOnFullBasic(pfOnFullBasic);
        s.setEsiEnabled(esiEnabled);
        s.setEsiEmployeeRate(esiEmployeeRate);
        s.setEsiEmployerRate(esiEmployerRate);
        s.setEsiWageThreshold(esiWageThreshold);
        s.setPtEnabled(ptEnabled);
        s.setPtState(ptState == null || ptState.isBlank() ? null : ptState.trim().toUpperCase(java.util.Locale.ROOT));
        s.setPtFixedOverride(ptFixedOverride);
        s.setPtFixedAmount(ptFixedAmount == null ? BigDecimal.ZERO : ptFixedAmount);
        s.setTdsEnabled(tdsEnabled);
    }

    public boolean isPfEnabled() { return pfEnabled; }
    public void setPfEnabled(boolean pfEnabled) { this.pfEnabled = pfEnabled; }
    public BigDecimal getPfEmployeeRate() { return pfEmployeeRate; }
    public void setPfEmployeeRate(BigDecimal pfEmployeeRate) { this.pfEmployeeRate = pfEmployeeRate; }
    public BigDecimal getPfEmployerRate() { return pfEmployerRate; }
    public void setPfEmployerRate(BigDecimal pfEmployerRate) { this.pfEmployerRate = pfEmployerRate; }
    public BigDecimal getEpsRate() { return epsRate; }
    public void setEpsRate(BigDecimal epsRate) { this.epsRate = epsRate; }
    public BigDecimal getPfWageCeiling() { return pfWageCeiling; }
    public void setPfWageCeiling(BigDecimal pfWageCeiling) { this.pfWageCeiling = pfWageCeiling; }
    public boolean isPfOnFullBasic() { return pfOnFullBasic; }
    public void setPfOnFullBasic(boolean pfOnFullBasic) { this.pfOnFullBasic = pfOnFullBasic; }
    public boolean isEsiEnabled() { return esiEnabled; }
    public void setEsiEnabled(boolean esiEnabled) { this.esiEnabled = esiEnabled; }
    public BigDecimal getEsiEmployeeRate() { return esiEmployeeRate; }
    public void setEsiEmployeeRate(BigDecimal esiEmployeeRate) { this.esiEmployeeRate = esiEmployeeRate; }
    public BigDecimal getEsiEmployerRate() { return esiEmployerRate; }
    public void setEsiEmployerRate(BigDecimal esiEmployerRate) { this.esiEmployerRate = esiEmployerRate; }
    public BigDecimal getEsiWageThreshold() { return esiWageThreshold; }
    public void setEsiWageThreshold(BigDecimal esiWageThreshold) { this.esiWageThreshold = esiWageThreshold; }
    public boolean isPtEnabled() { return ptEnabled; }
    public void setPtEnabled(boolean ptEnabled) { this.ptEnabled = ptEnabled; }
    public String getPtState() { return ptState; }
    public void setPtState(String ptState) { this.ptState = ptState; }
    public boolean isPtFixedOverride() { return ptFixedOverride; }
    public void setPtFixedOverride(boolean ptFixedOverride) { this.ptFixedOverride = ptFixedOverride; }
    public BigDecimal getPtFixedAmount() { return ptFixedAmount; }
    public void setPtFixedAmount(BigDecimal ptFixedAmount) { this.ptFixedAmount = ptFixedAmount; }
    public boolean isTdsEnabled() { return tdsEnabled; }
    public void setTdsEnabled(boolean tdsEnabled) { this.tdsEnabled = tdsEnabled; }
    public boolean isConfigured() { return configured; }
    public String getResolvedPtState() { return resolvedPtState; }
    public List<PtStateOption> getPtStates() { return ptStates; }
}
