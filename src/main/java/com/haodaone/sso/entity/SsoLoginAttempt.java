package com.haodaone.sso.entity;

import com.haodaone.user.entity.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One SSO sign-in: created at /start (hashed state, nonce, PKCE verifier,
 * browser-binding cookie hash), completed at /callback (user + hashed one-time
 * exchange code), consumed by POST /exchange.
 */
@Entity
@Table(name = "sso_login_attempt")
public class SsoLoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "state_hash", nullable = false, unique = true, length = 64)
    private String stateHash;

    @Column(name = "browser_binding_hash", nullable = false, length = 64)
    private String browserBindingHash;

    @Column(nullable = false, length = 100)
    private String nonce;

    @Column(name = "code_verifier", nullable = false, length = 128)
    private String codeVerifier;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "callback_at")
    private LocalDateTime callbackAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "exchange_code_hash", unique = true, length = 64)
    private String exchangeCodeHash;

    @Column(name = "exchange_expires_at")
    private LocalDateTime exchangeExpiresAt;

    @Column(name = "exchanged_at")
    private LocalDateTime exchangedAt;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getStateHash() { return stateHash; }
    public void setStateHash(String stateHash) { this.stateHash = stateHash; }
    public String getBrowserBindingHash() { return browserBindingHash; }
    public void setBrowserBindingHash(String browserBindingHash) { this.browserBindingHash = browserBindingHash; }
    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }
    public String getCodeVerifier() { return codeVerifier; }
    public void setCodeVerifier(String codeVerifier) { this.codeVerifier = codeVerifier; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public LocalDateTime getCallbackAt() { return callbackAt; }
    public void setCallbackAt(LocalDateTime callbackAt) { this.callbackAt = callbackAt; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getExchangeCodeHash() { return exchangeCodeHash; }
    public void setExchangeCodeHash(String exchangeCodeHash) { this.exchangeCodeHash = exchangeCodeHash; }
    public LocalDateTime getExchangeExpiresAt() { return exchangeExpiresAt; }
    public void setExchangeExpiresAt(LocalDateTime exchangeExpiresAt) { this.exchangeExpiresAt = exchangeExpiresAt; }
    public LocalDateTime getExchangedAt() { return exchangedAt; }
    public void setExchangedAt(LocalDateTime exchangedAt) { this.exchangedAt = exchangedAt; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
