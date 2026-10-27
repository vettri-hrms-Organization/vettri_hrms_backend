ALTER TABLE monitored_device
    ADD COLUMN hardware_inventory JSONB,
    ADD COLUMN inventory_status VARCHAR(20) NOT NULL DEFAULT 'NOT_SCANNED',
    ADD COLUMN last_inventory_scan_at TIMESTAMP(6) WITHOUT TIME ZONE;

CREATE TABLE device_inventory_scan (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    device_id BIGINT NOT NULL REFERENCES monitored_device(id),
    request_id UUID NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    completed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    error_code VARCHAR(80),
    error_message VARCHAR(300),
    CONSTRAINT chk_device_inventory_scan_status
        CHECK (status IN ('PENDING', 'SCANNING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_device_inventory_scan_device_status
    ON device_inventory_scan(device_id, status, requested_at DESC)
    WHERE deleted = FALSE;
