CREATE TABLE employee_invitation (
    id BIGSERIAL PRIMARY KEY,
    employee_id BIGINT NOT NULL REFERENCES employee(id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    used_at TIMESTAMP(6) WITHOUT TIME ZONE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uq_employee_invitation_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_employee_invitation_employee ON employee_invitation(employee_id);
CREATE INDEX idx_employee_invitation_token_hash ON employee_invitation(token_hash);
CREATE UNIQUE INDEX uq_employee_invitation_pending_employee
    ON employee_invitation(employee_id) WHERE status = 'PENDING';