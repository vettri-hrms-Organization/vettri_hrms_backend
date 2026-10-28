package com.haodaone.auth.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Per-user two-factor state. Kept out of app_user so the login identity row
 * stays small and the MFA secrets live in one place. The TOTP seed is AES-GCM
 * encrypted (MfaSecretCipher); totpLastUsedStep gives replay protection - a
 * code is accepted only for a time step newer than the last accepted one.
 */
@Entity
@Table(name = "user_mfa")
public class UserMfa extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "totp_secret_enc", columnDefinition = "TEXT")
    private String totpSecretEnc;

    /** Secret handed out by /totp/enroll but not yet confirmed with a valid code. */
    @Column(name = "totp_pending_secret_enc", columnDefinition = "TEXT")
    private String totpPendingSecretEnc;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Column(name = "totp_last_used_step")
    private Long totpLastUsedStep;

    @Column(name = "email_otp_enabled", nullable = false)
    private boolean emailOtpEnabled = false;

    @Column(name = "enabled_at")
    private LocalDateTime enabledAt;

    public boolean isEnabled() {
        return totpEnabled || emailOtpEnabled;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getTotpSecretEnc() { return totpSecretEnc; }
    public void setTotpSecretEnc(String totpSecretEnc) { this.totpSecretEnc = totpSecretEnc; }
    public String getTotpPendingSecretEnc() { return totpPendingSecretEnc; }
    public void setTotpPendingSecretEnc(String totpPendingSecretEnc) { this.totpPendingSecretEnc = totpPendingSecretEnc; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
    public Long getTotpLastUsedStep() { return totpLastUsedStep; }
    public void setTotpLastUsedStep(Long totpLastUsedStep) { this.totpLastUsedStep = totpLastUsedStep; }
    public boolean isEmailOtpEnabled() { return emailOtpEnabled; }
    public void setEmailOtpEnabled(boolean emailOtpEnabled) { this.emailOtpEnabled = emailOtpEnabled; }
    public LocalDateTime getEnabledAt() { return enabledAt; }
    public void setEnabledAt(LocalDateTime enabledAt) { this.enabledAt = enabledAt; }
}
