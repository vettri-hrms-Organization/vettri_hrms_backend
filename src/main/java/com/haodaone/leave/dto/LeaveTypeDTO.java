package com.haodaone.leave.dto;

import com.haodaone.leave.entity.LeaveType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class LeaveTypeDTO {
    private Long id;
    private String name;
    private String code;
    private double defaultDaysPerYear;
    private boolean carryForward;
    private boolean autoApprove;
    private boolean active;
    private boolean paid;

    public static LeaveTypeDTO from(LeaveType t) {
        LeaveTypeDTO dto = new LeaveTypeDTO();
        dto.id = t.getId();
        dto.name = t.getName();
        dto.code = t.getCode();
        dto.defaultDaysPerYear = t.getDefaultDaysPerYear();
        dto.carryForward = t.isCarryForward();
        dto.autoApprove = t.isAutoApprove();
        dto.active = t.isActive();
        dto.paid = t.isPaid();
        dto.policy = LeavePolicyDTOs.PolicyView.of(t);
        return dto;
    }

    /** Accrual / carry-forward / encashment policy (V45). */
    private LeavePolicyDTOs.PolicyView policy;

    public LeavePolicyDTOs.PolicyView getPolicy() {
        return policy;
    }

    public boolean isPaid() {
        return paid;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public double getDefaultDaysPerYear() {
        return defaultDaysPerYear;
    }

    public boolean isCarryForward() {
        return carryForward;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isAutoApprove() {
        return autoApprove;
    }

    public static class CreateRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotBlank(message = "Code is required")
        private String code;

        @Positive(message = "Days per year must be positive")
        private double defaultDaysPerYear;

        private boolean carryForward;
        private boolean autoApprove;
        /** Null = paid (create) / unchanged (update), so older clients never flip a type to unpaid by omission. */
        private Boolean paid;

        public Boolean getPaid() {
            return paid;
        }

        public void setPaid(Boolean paid) {
            this.paid = paid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public double getDefaultDaysPerYear() {
            return defaultDaysPerYear;
        }

        public void setDefaultDaysPerYear(double defaultDaysPerYear) {
            this.defaultDaysPerYear = defaultDaysPerYear;
        }

        public boolean isCarryForward() {
            return carryForward;
        }

        public void setCarryForward(boolean carryForward) {
            this.carryForward = carryForward;
        }

        public boolean isAutoApprove() {
            return autoApprove;
        }

        public void setAutoApprove(boolean autoApprove) {
            this.autoApprove = autoApprove;
        }
    }
}
