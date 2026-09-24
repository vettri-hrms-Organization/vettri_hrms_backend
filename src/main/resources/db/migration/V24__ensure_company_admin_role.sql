INSERT INTO role (version, created_at, updated_at, deleted, name, label, description, system_defined, company_id)
SELECT 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE,
       'COMPANY_ADMIN',
       'COMPANY_ADMIN',
       'Company-level administrator (tenant-scoped)',
       TRUE,
       NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM role
    WHERE name = 'COMPANY_ADMIN'
      AND company_id IS NULL
);