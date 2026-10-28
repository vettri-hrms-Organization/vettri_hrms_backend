package com.haodaone.sso.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Link between a Vettri user and an external (provider, subject) identity, set on first SSO sign-in. */
@Entity
@Table(name = "user_sso_identity",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_sso_identity", columnNames = {"provider", "subject"}))
public class UserSsoIdentity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(nullable = false, length = 255)
    private String issuer;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(length = 150)
    private String email;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
