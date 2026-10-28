-- Go-live payroll: bank/statutory identity on the employee, unpaid (loss of pay)
-- leave types, and the paid/LOP day counts each payroll line was prorated with.

-- Bank transfer + statutory identifiers. All optional: payroll still runs for an
-- employee without them, the bank transfer export just leaves the cells blank.
ALTER TABLE employee ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(34);
ALTER TABLE employee ADD COLUMN IF NOT EXISTS bank_ifsc VARCHAR(11);
ALTER TABLE employee ADD COLUMN IF NOT EXISTS bank_name VARCHAR(100);
ALTER TABLE employee ADD COLUMN IF NOT EXISTS pan_number VARCHAR(10);
ALTER TABLE employee ADD COLUMN IF NOT EXISTS uan_number VARCHAR(12);
ALTER TABLE employee ADD COLUMN IF NOT EXISTS esi_number VARCHAR(17);

-- Every existing leave type stays paid; approved days of an unpaid type are
-- deducted as loss of pay when a payroll run is generated.
ALTER TABLE leave_type ADD COLUMN IF NOT EXISTS paid BOOLEAN NOT NULL DEFAULT TRUE;
UPDATE leave_type SET paid = FALSE WHERE UPPER(code) IN ('LOP', 'LWP', 'UL');

-- Days the line was paid for / docked as loss of pay. paid_days stays NULL on
-- items generated before proration existed (they were always full month).
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS lop_days NUMERIC(5,1) NOT NULL DEFAULT 0;
ALTER TABLE payroll_item ADD COLUMN IF NOT EXISTS paid_days NUMERIC(5,1);
