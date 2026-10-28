-- Expense claims (categories, settings, claims, claim lines) and
-- announcements with read/acknowledgement receipts.

CREATE TABLE expense_category (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    code VARCHAR(40) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    per_claim_limit NUMERIC(12, 2),
    limit_action VARCHAR(10) NOT NULL DEFAULT 'WARN',
    receipt_required_above NUMERIC(12, 2),
    receipt_action VARCHAR(10) NOT NULL DEFAULT 'WARN',
    mileage_rate_per_km NUMERIC(8, 2),
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_expense_category_limit_action CHECK (limit_action IN ('WARN', 'BLOCK')),
    CONSTRAINT chk_expense_category_receipt_action CHECK (receipt_action IN ('WARN', 'BLOCK'))
);

CREATE UNIQUE INDEX ux_expense_category_company_code
    ON expense_category(company_id, code) WHERE deleted = FALSE;

CREATE TABLE expense_settings (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    reimbursement_mode VARCHAR(20) NOT NULL DEFAULT 'SEPARATE',
    CONSTRAINT chk_expense_settings_mode CHECK (reimbursement_mode IN ('PAYROLL', 'SEPARATE'))
);

CREATE UNIQUE INDEX ux_expense_settings_company
    ON expense_settings(company_id) WHERE deleted = FALSE;

CREATE TABLE expense_claim (
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
    title VARCHAR(200) NOT NULL,
    purpose VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    submitted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    manager_approved_by_employee_id BIGINT REFERENCES employee(id),
    manager_approved_at TIMESTAMP(6) WITHOUT TIME ZONE,
    manager_note VARCHAR(500),
    approved_by_employee_id BIGINT REFERENCES employee(id),
    approved_at TIMESTAMP(6) WITHOUT TIME ZONE,
    approver_note VARCHAR(500),
    rejected_by_employee_id BIGINT REFERENCES employee(id),
    rejected_at TIMESTAMP(6) WITHOUT TIME ZONE,
    rejection_reason VARCHAR(500),
    rejected_stage VARCHAR(30),
    reimbursed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    reimbursement_method VARCHAR(20),
    payment_reference VARCHAR(120),
    payroll_item_id BIGINT,
    policy_warnings VARCHAR(2000),
    CONSTRAINT chk_expense_claim_status
        CHECK (status IN ('DRAFT', 'SUBMITTED', 'MANAGER_APPROVED', 'APPROVED', 'REJECTED', 'REIMBURSED'))
);

CREATE INDEX idx_expense_claim_company_status ON expense_claim(company_id, status) WHERE deleted = FALSE;
CREATE INDEX idx_expense_claim_company_employee ON expense_claim(company_id, employee_id) WHERE deleted = FALSE;

CREATE TABLE expense_claim_line (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    claim_id BIGINT NOT NULL REFERENCES expense_claim(id),
    category_id BIGINT NOT NULL REFERENCES expense_category(id),
    expense_date DATE NOT NULL,
    description VARCHAR(500),
    amount NUMERIC(12, 2) NOT NULL,
    mileage_km NUMERIC(10, 2),
    mileage_rate NUMERIC(8, 2),
    receipt_s3_key VARCHAR(500),
    receipt_file_name VARCHAR(255),
    receipt_content_type VARCHAR(120),
    receipt_size_bytes BIGINT
);

CREATE INDEX idx_expense_claim_line_claim ON expense_claim_line(claim_id) WHERE deleted = FALSE;

CREATE TABLE announcement (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    category VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    audience_type VARCHAR(20) NOT NULL DEFAULT 'ALL',
    audience_department_ids VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    publish_at TIMESTAMP(6) WITHOUT TIME ZONE,
    published_at TIMESTAMP(6) WITHOUT TIME ZONE,
    expires_at TIMESTAMP(6) WITHOUT TIME ZONE,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    requires_acknowledgement BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledgement_due_date DATE,
    attachment_s3_key VARCHAR(500),
    attachment_file_name VARCHAR(255),
    attachment_content_type VARCHAR(120),
    attachment_size_bytes BIGINT,
    author_name VARCHAR(150),
    notified_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT chk_announcement_category CHECK (category IN ('GENERAL', 'POLICY', 'EVENT', 'HOLIDAY', 'URGENT')),
    CONSTRAINT chk_announcement_audience CHECK (audience_type IN ('ALL', 'DEPARTMENTS')),
    CONSTRAINT chk_announcement_status CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED'))
);

CREATE INDEX idx_announcement_company_status ON announcement(company_id, status) WHERE deleted = FALSE;
CREATE INDEX idx_announcement_scheduled ON announcement(status, publish_at) WHERE deleted = FALSE;

CREATE TABLE announcement_receipt (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    announcement_id BIGINT NOT NULL REFERENCES announcement(id),
    employee_id BIGINT NOT NULL REFERENCES employee(id),
    read_at TIMESTAMP(6) WITHOUT TIME ZONE,
    acknowledged_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT ux_announcement_receipt UNIQUE (announcement_id, employee_id)
);

CREATE INDEX idx_announcement_receipt_employee ON announcement_receipt(company_id, employee_id);
