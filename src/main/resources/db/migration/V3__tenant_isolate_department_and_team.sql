-- Tenant-isolate Department and Team on the fresh database.
-- This migration is intentionally additive and does not backfill historical data.
-- The database is newly created, so there is no legacy business data to preserve.

ALTER TABLE department
    ADD COLUMN company_id BIGINT NOT NULL REFERENCES company(id);

ALTER TABLE team
    ADD COLUMN company_id BIGINT NOT NULL REFERENCES company(id);

CREATE INDEX idx_department_company_id ON department(company_id);
CREATE INDEX idx_team_company_id ON team(company_id);
