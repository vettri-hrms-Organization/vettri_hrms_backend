package com.haodaone.notifications.whatsapp;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A user's explicit WhatsApp opt-in (number with country code + timestamp), required by WhatsApp policy. */
@Entity
@Table(name = "whatsapp_opt_in")
public class WhatsAppOptIn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "phone_e164", nullable = false, length = 20)
    private String phoneE164;

    @Column(name = "opted_in", nullable = false)
    private boolean optedIn = false;

    @Column(name = "opted_in_at")
    private LocalDateTime optedInAt;

    @Column(name = "opted_out_at")
    private LocalDateTime optedOutAt;

    @Column(name = "consent_ip", length = 64)
    private String consentIp;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPhoneE164() { return phoneE164; }
    public void setPhoneE164(String phoneE164) { this.phoneE164 = phoneE164; }
    public boolean isOptedIn() { return optedIn; }
    public void setOptedIn(boolean optedIn) { this.optedIn = optedIn; }
    public LocalDateTime getOptedInAt() { return optedInAt; }
    public void setOptedInAt(LocalDateTime optedInAt) { this.optedInAt = optedInAt; }
    public LocalDateTime getOptedOutAt() { return optedOutAt; }
    public void setOptedOutAt(LocalDateTime optedOutAt) { this.optedOutAt = optedOutAt; }
    public String getConsentIp() { return consentIp; }
    public void setConsentIp(String consentIp) { this.consentIp = consentIp; }
}
