CREATE TABLE remote_desktop_session (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    requested_by BIGINT,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    failure_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_remote_desktop_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_remote_desktop_device FOREIGN KEY (device_id) REFERENCES monitored_device(id)
);

CREATE INDEX ix_remote_desktop_device_status ON remote_desktop_session(device_id, status, created_at);