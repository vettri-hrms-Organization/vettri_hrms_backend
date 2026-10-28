-- Privacy & DPDP Act 2023 compliance (com.haodaone.privacy):
--  * legal_document         - registry of published legal document versions (synced from app.legal.* config)
--  * legal_acceptance       - who accepted which document version, when, from where
--  * company_privacy_setting- grievance officer, breach contact, retention, monitoring-consent policy
--  * monitoring_consent     - employee consent to the IT monitoring agent (grant / withdrawal history)
--  * data_principal_request - access / correction / erasure requests with a 30-day SLA
--  * company_deletion_request - company account deletion with a 30-day cancellable grace period

CREATE TABLE IF NOT EXISTS legal_document (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    doc_type VARCHAR(30) NOT NULL,
    doc_version VARCHAR(40) NOT NULL,
    title VARCHAR(150) NOT NULL,
    url VARCHAR(500) NOT NULL,
    published_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_legal_document_type_version UNIQUE (doc_type, doc_version)
);

CREATE TABLE IF NOT EXISTS legal_acceptance (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT REFERENCES company(id),
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    doc_type VARCHAR(30) NOT NULL,
    doc_version VARCHAR(40) NOT NULL,
    accepted_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    on_behalf_of_company BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_legal_acceptance_user ON legal_acceptance(user_id, doc_type);
CREATE INDEX IF NOT EXISTS idx_legal_acceptance_company ON legal_acceptance(company_id);

CREATE TABLE IF NOT EXISTS company_privacy_setting (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL UNIQUE REFERENCES company(id),
    grievance_officer_name VARCHAR(150),
    grievance_officer_email VARCHAR(150),
    grievance_officer_phone VARCHAR(30),
    breach_contact_name VARCHAR(150),
    breach_contact_email VARCHAR(150),
    breach_contact_phone VARCHAR(30),
    payroll_retention_years INT NOT NULL DEFAULT 7,
    other_data_retention_months INT NOT NULL DEFAULT 24,
    monitoring_consent_required BOOLEAN NOT NULL DEFAULT FALSE,
    stop_monitoring_on_withdrawal BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS monitoring_consent (
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
    user_id BIGINT REFERENCES app_user(id),
    notice_version VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL,
    granted_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    withdrawn_at TIMESTAMP(6) WITHOUT TIME ZONE,
    withdrawal_reason VARCHAR(1000),
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    CONSTRAINT chk_monitoring_consent_status CHECK (status IN ('GRANTED', 'WITHDRAWN'))
);
CREATE INDEX IF NOT EXISTS idx_monitoring_consent_employee ON monitoring_consent(company_id, employee_id);

CREATE TABLE IF NOT EXISTS data_principal_request (
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
    requested_by_user_id BIGINT,
    request_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    details VARCHAR(4000),
    due_date DATE NOT NULL,
    resolution_note VARCHAR(4000),
    resolved_at TIMESTAMP(6) WITHOUT TIME ZONE,
    resolved_by_user_id BIGINT,
    ready_at TIMESTAMP(6) WITHOUT TIME ZONE,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE,
    download_count INT NOT NULL DEFAULT 0,
    last_downloaded_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT chk_dpr_type CHECK (request_type IN ('ACCESS', 'CORRECTION', 'ERASURE')),
    CONSTRAINT chk_dpr_status CHECK (status IN ('SUBMITTED', 'IN_PROGRESS', 'READY', 'COMPLETED', 'REJECTED'))
);
CREATE INDEX IF NOT EXISTS idx_dpr_company_status ON data_principal_request(company_id, status);
CREATE INDEX IF NOT EXISTS idx_dpr_employee ON data_principal_request(employee_id);

CREATE TABLE IF NOT EXISTS company_deletion_request (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    requested_by_user_id BIGINT,
    requested_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    scheduled_for TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(2000),
    cancelled_at TIMESTAMP(6) WITHOUT TIME ZONE,
    cancelled_by_user_id BIGINT,
    completed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    completion_note VARCHAR(4000),
    CONSTRAINT chk_company_deletion_status CHECK (status IN ('PENDING', 'CANCELLED', 'COMPLETED'))
);
CREATE INDEX IF NOT EXISTS idx_company_deletion_status ON company_deletion_request(status, scheduled_for);
