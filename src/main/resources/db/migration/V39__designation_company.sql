-- Designations had no company, so titles created without a department were shared
-- by every tenant and any tenant could attach another tenant's designation.
ALTER TABLE designation ADD COLUMN IF NOT EXISTS company_id BIGINT REFERENCES company(id);

-- Backfill: from the designation's department, else from whoever uses it.
UPDATE designation d SET company_id = dep.company_id
FROM department dep
WHERE d.department_id = dep.id AND d.company_id IS NULL;

UPDATE designation d SET company_id = (SELECT MIN(e.company_id) FROM employee e WHERE e.designation_id = d.id)
WHERE d.company_id IS NULL;

UPDATE designation d SET company_id = (SELECT MIN(j.company_id) FROM job_opening j WHERE j.designation_id = d.id)
WHERE d.company_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_designation_company_id ON designation (company_id);
