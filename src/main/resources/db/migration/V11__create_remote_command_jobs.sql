CREATE TABLE remote_command_job (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    requested_by BIGINT,
    command TEXT NOT NULL,
    shell_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    stdout TEXT,
    stderr TEXT,
    exit_code INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    timeout_seconds INTEGER NOT NULL DEFAULT 30,
    error_message VARCHAR(2000),
    correlation_id VARCHAR(80) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_remote_command_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_remote_command_device FOREIGN KEY (device_id) REFERENCES monitored_device(id)
);

CREATE INDEX ix_remote_command_device_status ON remote_command_job(device_id, status, created_at);
CREATE INDEX ix_remote_command_company_created ON remote_command_job(company_id, created_at);