-- Two-factor authentication (TOTP authenticator app, email OTP, recovery codes,
-- trusted devices) and the per-company 2FA policy. Every secret/code is stored
-- hashed (SHA-256) except the TOTP seed, which must be recoverable and is
-- therefore AES-GCM encrypted (see auth.service.MfaSecretCipher).

CREATE TABLE IF NOT EXISTS user_mfa (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT REFERENCES company(id),
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id),
    totp_secret_enc TEXT,
    totp_pending_secret_enc TEXT,
    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    totp_last_used_step BIGINT,
    email_otp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    enabled_at TIMESTAMP(6) WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS mfa_recovery_code (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    code_hash VARCHAR(64) NOT NULL,
    used_at TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mfa_recovery_code_user ON mfa_recovery_code(user_id);

CREATE TABLE IF NOT EXISTS mfa_challenge (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    methods VARCHAR(60) NOT NULL,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    consumed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    failed_attempts INT NOT NULL DEFAULT 0,
    ip_address VARCHAR(64),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mfa_challenge_user ON mfa_challenge(user_id);

CREATE TABLE IF NOT EXISTS mfa_email_otp (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    purpose VARCHAR(20) NOT NULL,
    challenge_id BIGINT REFERENCES mfa_challenge(id),
    salt VARCHAR(64) NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    sent_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    consumed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mfa_email_otp_purpose CHECK (purpose IN ('LOGIN', 'SETUP'))
);
CREATE INDEX IF NOT EXISTS idx_mfa_email_otp_user ON mfa_email_otp(user_id, purpose, sent_at DESC);

CREATE TABLE IF NOT EXISTS mfa_trusted_device (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    revoked_at TIMESTAMP(6) WITHOUT TIME ZONE,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_mfa_trusted_device_user ON mfa_trusted_device(user_id);

CREATE TABLE IF NOT EXISTS company_security_setting (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL UNIQUE REFERENCES company(id),
    two_factor_policy VARCHAR(30) NOT NULL DEFAULT 'OPTIONAL',
    CONSTRAINT chk_company_security_two_factor_policy
        CHECK (two_factor_policy IN ('OFF', 'OPTIONAL', 'REQUIRED_ADMINS', 'REQUIRED_ALL'))
);
