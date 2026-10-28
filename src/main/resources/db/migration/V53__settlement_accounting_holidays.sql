-- V53: Full & Final settlement + gratuity, payroll accounting export ledger
-- mapping, and location-aware / optional holidays.

-- ---------------------------------------------------------------- holidays
-- MANDATORY = a holiday for everyone it applies to; OPTIONAL = restricted
-- holiday that only counts for employees who pick it (holiday_settings quota).
ALTER TABLE holiday ADD COLUMN IF NOT EXISTS holiday_type VARCHAR(20) NOT NULL DEFAULT 'MANDATORY';

-- Office locations a holiday applies to. No rows = applies to every location.
CREATE TABLE IF NOT EXISTS holiday_location (
    holiday_id BIGINT NOT NULL REFERENCES holiday(id),
    office_location_id BIGINT NOT NULL REFERENCES office_location(id),
    PRIMARY KEY (holiday_id, office_location_id)
);

-- Employee work location (nullable: no location = only company-wide holidays apply).
ALTER TABLE employee ADD COLUMN IF NOT EXISTS office_location_id BIGINT NULL REFERENCES office_location(id);

-- One row per company; a company without a row uses the entity defaults.
CREATE TABLE IF NOT EXISTS holiday_settings (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL UNIQUE REFERENCES company(id),
    max_optional_holidays INTEGER NOT NULL DEFAULT 2
);

-- Optional holidays an employee picked (soft-deleted when un-picked).
CREATE TABLE IF NOT EXISTS optional_holiday_selection (
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
    holiday_id BIGINT NOT NULL REFERENCES holiday(id),
    holiday_year INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_optional_holiday_sel_emp_year ON optional_holiday_selection(company_id, employee_id, holiday_year);
CREATE UNIQUE INDEX IF NOT EXISTS uq_optional_holiday_sel_active ON optional_holiday_selection(employee_id, holiday_id) WHERE deleted = FALSE;

-- ---------------------------------------------------------------- settlement
CREATE TABLE IF NOT EXISTS settlement_settings (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL UNIQUE REFERENCES company(id),
    gratuity_applicable BOOLEAN NOT NULL DEFAULT TRUE,
    gratuity_covered_under_act BOOLEAN NOT NULL DEFAULT TRUE,
    gratuity_ceiling NUMERIC(14,2) NOT NULL DEFAULT 2000000.00,
    -- 0 = off; 240 (6-day week) or 190 (5-day week): days worked in the 5th year that make 4 years count as 5.
    gratuity_partial_year_days INTEGER NOT NULL DEFAULT 0,
    default_notice_period_days INTEGER NOT NULL DEFAULT 30,
    notice_recovery_basis VARCHAR(10) NOT NULL DEFAULT 'BASIC'
);

CREATE TABLE IF NOT EXISTS final_settlement (
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
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    resignation_date DATE,
    last_working_date DATE NOT NULL,
    notice_period_days INTEGER NOT NULL DEFAULT 0,
    notice_served_days INTEGER NOT NULL DEFAULT 0,
    notice_shortfall_days INTEGER NOT NULL DEFAULT 0,
    notice_waived BOOLEAN NOT NULL DEFAULT FALSE,
    notice_recovery_basis VARCHAR(10) NOT NULL DEFAULT 'BASIC',
    death_or_disablement BOOLEAN NOT NULL DEFAULT FALSE,
    service_years INTEGER NOT NULL DEFAULT 0,
    service_months INTEGER NOT NULL DEFAULT 0,
    service_days INTEGER NOT NULL DEFAULT 0,
    gratuity_years INTEGER NOT NULL DEFAULT 0,
    gratuity_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    gratuity_wage NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_earnings NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_deductions NUMERIC(14,2) NOT NULL DEFAULT 0,
    taxable_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    tds_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    net_payable NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes VARCHAR(1000),
    computation_notes VARCHAR(2000),
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP(6) WITHOUT TIME ZONE,
    approved_by VARCHAR(100),
    approved_at TIMESTAMP(6) WITHOUT TIME ZONE,
    payment_reference VARCHAR(120),
    payment_date DATE,
    paid_at TIMESTAMP(6) WITHOUT TIME ZONE
);
CREATE INDEX IF NOT EXISTS idx_final_settlement_company_status ON final_settlement(company_id, status);
CREATE INDEX IF NOT EXISTS idx_final_settlement_employee ON final_settlement(employee_id);

CREATE TABLE IF NOT EXISTS final_settlement_line (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT REFERENCES company(id),
    settlement_id BIGINT NOT NULL REFERENCES final_settlement(id),
    kind VARCHAR(12) NOT NULL,
    code VARCHAR(50) NOT NULL,
    label VARCHAR(200) NOT NULL,
    amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    taxable_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    manual BOOLEAN NOT NULL DEFAULT FALSE,
    source_type VARCHAR(50),
    source_id BIGINT,
    sort_order INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_final_settlement_line_settlement ON final_settlement_line(settlement_id);

-- ---------------------------------------------------------------- accounting export
CREATE TABLE IF NOT EXISTS payroll_ledger_settings (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL UNIQUE REFERENCES company(id),
    salaries_ledger VARCHAR(150) NOT NULL DEFAULT 'Salaries & Wages',
    employer_pf_ledger VARCHAR(150) NOT NULL DEFAULT 'Employer PF Contribution',
    employer_esi_ledger VARCHAR(150) NOT NULL DEFAULT 'Employer ESI Contribution',
    reimbursement_expense_ledger VARCHAR(150) NOT NULL DEFAULT 'Employee Reimbursements',
    salary_payable_ledger VARCHAR(150) NOT NULL DEFAULT 'Salary Payable',
    pf_payable_ledger VARCHAR(150) NOT NULL DEFAULT 'PF Payable',
    esi_payable_ledger VARCHAR(150) NOT NULL DEFAULT 'ESI Payable',
    pt_payable_ledger VARCHAR(150) NOT NULL DEFAULT 'Professional Tax Payable',
    tds_payable_ledger VARCHAR(150) NOT NULL DEFAULT 'TDS Payable - Salaries',
    reimbursement_payable_ledger VARCHAR(150) NOT NULL DEFAULT 'Reimbursements Payable',
    other_deductions_ledger VARCHAR(150) NOT NULL DEFAULT 'Other Salary Deductions',
    voucher_type VARCHAR(60) NOT NULL DEFAULT 'Journal',
    tally_company_name VARCHAR(150)
);
