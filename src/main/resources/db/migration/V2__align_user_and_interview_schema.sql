-- Apply schema changes introduced after the initial migration was deployed.

UPDATE role
SET label = name
WHERE label IS NULL;

ALTER TABLE role
    ALTER COLUMN label SET NOT NULL;

ALTER TABLE permission
    RENAME COLUMN description TO label;

UPDATE permission
SET label = code
WHERE label IS NULL;

UPDATE permission
SET module = 'GENERAL'
WHERE module IS NULL;

ALTER TABLE permission
    ALTER COLUMN code TYPE VARCHAR(60),
    ALTER COLUMN label TYPE VARCHAR(150),
    ALTER COLUMN module TYPE VARCHAR(60),
    ALTER COLUMN label SET NOT NULL,
    ALTER COLUMN module SET NOT NULL;

ALTER TABLE employee
    ADD CONSTRAINT uq_employee_user UNIQUE (user_id);

CREATE INDEX idx_employee_user ON employee(user_id);

UPDATE interview
SET round_type = 'HR_INTERVIEW'
WHERE round_type IS NULL;

ALTER TABLE interview
    ALTER COLUMN round_type SET NOT NULL;