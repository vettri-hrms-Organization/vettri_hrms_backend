package com.haodaone.notifications.whatsapp;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Company switch for the WhatsApp channel and the events it covers. No row = off. */
@Entity
@Table(name = "company_whatsapp_setting")
public class CompanyWhatsAppSetting extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(nullable = false)
    private boolean enabled = false;

    /** Comma-separated WhatsAppEvent names. */
    @Column(length = 500)
    private String events;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEvents() { return events; }
    public void setEvents(String events) { this.events = events; }

    public Set<WhatsAppEvent> eventSet() {
        Set<WhatsAppEvent> result = EnumSet.noneOf(WhatsAppEvent.class);
        if (events == null) return result;
        Arrays.stream(events.split(",")).map(WhatsAppEvent::parse).forEach(e -> e.ifPresent(result::add));
        return result;
    }

    public void setEventSet(Set<WhatsAppEvent> set) {
        this.events = set == null || set.isEmpty() ? null
                : set.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }
}
