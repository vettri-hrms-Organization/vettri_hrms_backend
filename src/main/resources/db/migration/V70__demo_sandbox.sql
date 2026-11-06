-- V70: self-service demo workspaces. A customer can open a sandbox company (is_demo = TRUE) seeded with sample
-- data, linked to their real company; the real company never receives demo records. The sandbox expires with the
-- customer's trial and an hourly job cleans it up. onboarding_choice remembers the first-login choice on the real
-- company (SETUP or DEMO) so the welcome popup is not shown again. Additive only.

ALTER TABLE company ADD COLUMN IF NOT EXISTS demo_parent_company_id BIGINT REFERENCES company(id);
ALTER TABLE company ADD COLUMN IF NOT EXISTS demo_expires_at TIMESTAMP(6) WITHOUT TIME ZONE;
ALTER TABLE company ADD COLUMN IF NOT EXISTS demo_cleaned_at TIMESTAMP(6) WITHOUT TIME ZONE;
ALTER TABLE company ADD COLUMN IF NOT EXISTS demo_cleanup_error VARCHAR(500);
ALTER TABLE company ADD COLUMN IF NOT EXISTS onboarding_choice VARCHAR(20);

CREATE INDEX IF NOT EXISTS ix_company_demo_parent ON company (demo_parent_company_id) WHERE demo_parent_company_id IS NOT NULL;
