-- Round 2: employee custom fields, Google / Microsoft SSO, WhatsApp notifications
-- and sales demo workspaces. Everything is additive; features default to off.

-- ------------------------------------------------------------------ demo workspaces
ALTER TABLE company ADD COLUMN IF NOT EXISTS is_demo BOOLEAN NOT NULL DEFAULT FALSE;

-- ------------------------------------------------------------------ custom fields
CREATE TABLE IF NOT EXISTS custom_field_definition (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    field_key VARCHAR(60) NOT NULL,
    label VARCHAR(120) NOT NULL,
    field_type VARCHAR(20) NOT NULL,
    options TEXT,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    section VARCHAR(80),
    visible_to_employee BOOLEAN NOT NULL DEFAULT TRUE,
    editable_by_employee BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_custom_field_type CHECK (field_type IN
        ('TEXT', 'NUMBER', 'DATE', 'SELECT', 'MULTISELECT', 'BOOLEAN', 'EMAIL', 'PHONE'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_custom_field_definition_key
    ON custom_field_definition(company_id, field_key) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_custom_field_definition_company ON custom_field_definition(company_id, sort_order);

-- Values are stored as a normalised string per type (ISO date, plain decimal,
-- true/false, JSON array for MULTISELECT) and validated on every write.
CREATE TABLE IF NOT EXISTS employee_custom_field_value (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    employee_id BIGINT NOT NULL REFERENCES employee(id),
    definition_id BIGINT NOT NULL REFERENCES custom_field_definition(id),
    value_text TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_employee_custom_field_value
    ON employee_custom_field_value(employee_id, definition_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_employee_custom_field_value_company ON employee_custom_field_value(company_id);

-- ------------------------------------------------------------------ SSO
CREATE TABLE IF NOT EXISTS company_sso_setting (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL UNIQUE REFERENCES company(id),
    sso_only BOOLEAN NOT NULL DEFAULT FALSE,
    break_glass_user_id BIGINT REFERENCES app_user(id),
    auto_provision_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_provision_domains VARCHAR(500),
    trust_sso_mfa BOOLEAN NOT NULL DEFAULT FALSE
);

-- One row per SSO sign-in attempt: the redirect state (hashed) with its nonce
-- and PKCE verifier, then the one-time code the SPA exchanges for tokens.
CREATE TABLE IF NOT EXISTS sso_login_attempt (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    state_hash VARCHAR(64) NOT NULL UNIQUE,
    browser_binding_hash VARCHAR(64) NOT NULL,
    nonce VARCHAR(100) NOT NULL,
    code_verifier VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    callback_at TIMESTAMP(6) WITHOUT TIME ZONE,
    user_id BIGINT REFERENCES app_user(id),
    exchange_code_hash VARCHAR(64) UNIQUE,
    exchange_expires_at TIMESTAMP(6) WITHOUT TIME ZONE,
    exchanged_at TIMESTAMP(6) WITHOUT TIME ZONE,
    ip_address VARCHAR(64),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sso_login_attempt_expires ON sso_login_attempt(expires_at);

CREATE TABLE IF NOT EXISTS user_sso_identity (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    provider VARCHAR(20) NOT NULL,
    issuer VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email VARCHAR(150),
    last_login_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT uq_user_sso_identity UNIQUE (provider, subject)
);
CREATE INDEX IF NOT EXISTS idx_user_sso_identity_user ON user_sso_identity(user_id);

-- ------------------------------------------------------------------ WhatsApp
CREATE TABLE IF NOT EXISTS company_whatsapp_setting (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL UNIQUE REFERENCES company(id),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    events VARCHAR(500)
);

-- WhatsApp Business policy requires recorded, explicit opt-in per user.
CREATE TABLE IF NOT EXISTS whatsapp_opt_in (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    user_id BIGINT NOT NULL UNIQUE REFERENCES app_user(id),
    phone_e164 VARCHAR(20) NOT NULL,
    opted_in BOOLEAN NOT NULL DEFAULT FALSE,
    opted_in_at TIMESTAMP(6) WITHOUT TIME ZONE,
    opted_out_at TIMESTAMP(6) WITHOUT TIME ZONE,
    consent_ip VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS whatsapp_delivery_log (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    template_name VARCHAR(120),
    phone_masked VARCHAR(30),
    status VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(200),
    error_message VARCHAR(1000),
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_whatsapp_delivery_log_company ON whatsapp_delivery_log(company_id, created_at);
