-- Indian statutory payroll: company PF/ESI/PT/TDS settings, per-structure
-- auto-calculation flag, employer contributions and adjustment lines on
-- payroll items, and employee tax declarations (regime + investments).

-- One row per company. A company without a row uses the entity defaults.
CREATE TABLE IF NOT EXISTS payroll_statutory_settings (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT NOT NULL UNIQUE REFERENCES company(id),
    pf_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    pf_employee_rate NUMERIC(5,2) NOT NULL DEFAULT 12.00,
    pf_employer_rate NUMERIC(5,2) NOT NULL DEFAULT 12.00,
    eps_rate NUMERIC(5,2) NOT NULL DEFAULT 8.33,
    pf_wage_ceiling NUMERIC(14,2) NOT NULL DEFAULT 15000.00,
    pf_on_full_basic BOOLEAN NOT NULL DEFAULT FALSE,
    esi_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    esi_employee_rate NUMERIC(5,2) NOT NULL DEFAULT 0.75,
    esi_employer_rate NUMERIC(5,2) NOT NULL DEFAULT 3.25,
    esi_wage_threshold NUMERIC(14,2) NOT NULL DEFAULT 21000.00,
    pt_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    pt_state VARCHAR(10),
    pt_fixed_override BOOLEAN NOT NULL DEFAULT FALSE,
    pt_fixed_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    tds_enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- Existing structures keep their manually typed PF/ESI/PT/TDS (FALSE);
-- new structures saved through the API default to TRUE in the service.
ALTER TABLE salary_structure ADD COLUMN IF NOT EXISTS auto_statutory BOOLEAN NOT NULL DEFAULT FALSE;

-- Statutory wage bases and employer-side contributions (not deducted from pay).
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS pf_wages NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS eps_wages NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS edli_wages NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS employer_epf NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS employer_eps NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS employer_esi NUMERIC(14,2) NOT NULL DEFAULT 0;
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS adjustments_total NUMERIC(14,2) NOT NULL DEFAULT 0;
-- NULL on pre-V43 items: taxable gross = gross.
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS taxable_gross NUMERIC(14,2);
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS pt_state VARCHAR(10);
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS statutory_auto BOOLEAN NOT NULL DEFAULT FALSE;

-- Extra earnings lines contributed by PayrollAdjustmentProvider beans.
CREATE TABLE IF NOT EXISTS payroll_item_adjustment (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT REFERENCES company(id),
    payroll_item_id BIGINT NOT NULL REFERENCES payroll_item(id),
    code VARCHAR(50) NOT NULL,
    label VARCHAR(200) NOT NULL,
    amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    taxable BOOLEAN NOT NULL DEFAULT TRUE,
    source_type VARCHAR(50),
    source_id BIGINT,
    settled BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_payroll_item_adjustment_item ON payroll_item_adjustment(payroll_item_id);
CREATE INDEX IF NOT EXISTS idx_payroll_item_adjustment_source ON payroll_item_adjustment(source_type, source_id);

-- Employee tax regime + annual investment declaration per financial year
-- (financial_year = starting calendar year: 2026 = FY 2026-27).
CREATE TABLE IF NOT EXISTS employee_tax_declaration (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    company_id BIGINT REFERENCES company(id),
    employee_id BIGINT NOT NULL REFERENCES employee(id),
    financial_year INTEGER NOT NULL,
    regime VARCHAR(10) NOT NULL DEFAULT 'NEW',
    section_80c NUMERIC(14,2) NOT NULL DEFAULT 0,
    section_80d NUMERIC(14,2) NOT NULL DEFAULT 0,
    rent_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
    metro_city BOOLEAN NOT NULL DEFAULT FALSE,
    home_loan_interest NUMERIC(14,2) NOT NULL DEFAULT 0,
    nps_80ccd_1b NUMERIC(14,2) NOT NULL DEFAULT 0,
    other_deductions NUMERIC(14,2) NOT NULL DEFAULT 0,
    other_deductions_note VARCHAR(500),
    submitted_at TIMESTAMP(6) WITHOUT TIME ZONE,
    CONSTRAINT uk_tax_declaration_employee_fy UNIQUE (employee_id, financial_year)
);
CREATE INDEX IF NOT EXISTS idx_tax_declaration_company_fy ON employee_tax_declaration(company_id, financial_year);
