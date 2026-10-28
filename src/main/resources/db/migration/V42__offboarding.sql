-- Offboarding: record the employee's last working date so login can be
-- disabled automatically once it has passed (EmployeeExitScheduler).
ALTER TABLE employee ADD COLUMN IF NOT EXISTS last_working_date DATE NULL;
