CREATE TABLE device_enrollment (
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
    device_id BIGINT REFERENCES monitored_device(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    encrypted_token TEXT,
    device_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    used_at TIMESTAMP(6) WITHOUT TIME ZONE,
    revoked_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT chk_device_enrollment_status
        CHECK (status IN ('PENDING', 'USED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT chk_device_enrollment_pending_token
        CHECK (status <> 'PENDING' OR encrypted_token IS NOT NULL)
);

CREATE INDEX idx_device_enrollment_company_created
    ON device_enrollment(company_id, created_at DESC)
    WHERE deleted = FALSE;
