CREATE TABLE remote_support_job (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    requested_by BIGINT,
    operation VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    ultra_viewer_version VARCHAR(100),
    ultra_viewer_id VARCHAR(100),
    executable_path VARCHAR(500),
    running BOOLEAN,
    unattended_enabled BOOLEAN,
    error_code VARCHAR(80),
    error_message VARCHAR(2000),
    correlation_id VARCHAR(80) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_remote_support_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_remote_support_device FOREIGN KEY (device_id) REFERENCES monitored_device(id)
);
CREATE INDEX ix_remote_support_device_created ON remote_support_job(device_id, created_at);

CREATE TABLE remote_support_credential (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL UNIQUE,
    encrypted_secret TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    rotated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_remote_support_credential_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_remote_support_credential_device FOREIGN KEY (device_id) REFERENCES monitored_device(id)
);