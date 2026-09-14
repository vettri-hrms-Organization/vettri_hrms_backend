package com.haodaone.monitoring.entity;

import com.haodaone.company.entity.Company;
import com.haodaone.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "token_rotation_history")
public class TokenRotationHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "company_id") private Company company;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "device_id") private MonitoredDevice device;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "rotated_by_user_id") private User rotatedBy;
    @Column(name = "previous_agent_token_hash", length = 512) private String previousAgentTokenHash;
    @Column(name = "new_agent_token_hash", length = 512) private String newAgentTokenHash;
    @Column(name = "rotation_reason", length = 255) private String rotationReason;
    @Column(name = "rotation_ip", length = 64) private String rotationIp;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

    public void setCompany(Company value) { company = value; }
    public void setDevice(MonitoredDevice value) { device = value; }
    public void setRotatedBy(User value) { rotatedBy = value; }
    public void setPreviousAgentTokenHash(String value) { previousAgentTokenHash = value; }
    public void setNewAgentTokenHash(String value) { newAgentTokenHash = value; }
    public void setRotationReason(String value) { rotationReason = value; }
    public void setRotationIp(String value) { rotationIp = value; }
    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getRotationReason() { return rotationReason; }
    public String getRotationIp() { return rotationIp; }
    public String getPreviousAgentTokenHash() { return previousAgentTokenHash; }
    public String getNewAgentTokenHash() { return newAgentTokenHash; }
}