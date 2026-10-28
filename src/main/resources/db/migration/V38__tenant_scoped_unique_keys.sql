-- Uniqueness must hold inside one company, not across the whole platform.
-- V1 declared department.code, leave_type.code and employee.biometric_device_user_id
-- globally unique and payroll runs unique per period, so a second tenant could not
-- create department "HR", leave type "CL", map biometric PIN "1" or run October payroll.

-- Drop the single-column unique constraints on those columns, whatever they were named.
DO $$
DECLARE r RECORD;
BEGIN
    FOR r IN
        SELECT rel.relname AS table_name, con.conname AS constraint_name
        FROM pg_constraint con
        JOIN pg_class rel ON rel.oid = con.conrelid
        JOIN pg_attribute att ON att.attrelid = rel.oid AND att.attnum = con.conkey[1]
        WHERE con.contype = 'u'
          AND array_length(con.conkey, 1) = 1
          AND ((rel.relname IN ('department', 'leave_type') AND att.attname = 'code')
            OR (rel.relname = 'employee' AND att.attname = 'biometric_device_user_id'))
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.table_name, r.constraint_name);
    END LOOP;
END $$;

DROP INDEX IF EXISTS uq_payroll_run_period;

CREATE UNIQUE INDEX IF NOT EXISTS ux_department_company_code
    ON department (company_id, code) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS ux_leave_type_company_code
    ON leave_type (company_id, code) WHERE deleted = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS ux_employee_company_biometric_user
    ON employee (company_id, biometric_device_user_id)
    WHERE deleted = FALSE AND biometric_device_user_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_payroll_run_company_period
    ON payroll_run (company_id, period_year, period_month) WHERE deleted = FALSE;

-- Leave types seeded without a company belonged to nobody; retire them.
UPDATE leave_type SET deleted = TRUE, deleted_at = CURRENT_TIMESTAMP
WHERE company_id IS NULL AND deleted = FALSE;

-- Give every company that has no leave types the standard starter set.
INSERT INTO leave_type (company_id, name, code, default_days_per_year, carry_forward, active, auto_approve)
SELECT c.id, v.name, v.code, v.days, v.carry_forward, TRUE, FALSE
FROM company c
CROSS JOIN (VALUES ('Casual Leave', 'CL', 12.0, FALSE),
                   ('Sick Leave',   'SL', 10.0, FALSE),
                   ('Earned Leave', 'EL', 15.0, TRUE)) AS v(name, code, days, carry_forward)
WHERE NOT EXISTS (SELECT 1 FROM leave_type lt WHERE lt.company_id = c.id AND lt.deleted = FALSE);
