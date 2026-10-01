ALTER TABLE employee_document
    ADD COLUMN IF NOT EXISTS status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW';

ALTER TABLE employee_document
    ADD COLUMN IF NOT EXISTS s3_object_key VARCHAR(500);

ALTER TABLE employee_document
    ADD COLUMN IF NOT EXISTS original_file_name VARCHAR(255);

ALTER TABLE employee_document
    ADD COLUMN IF NOT EXISTS content_type VARCHAR(120);

ALTER TABLE employee_document
    ADD COLUMN IF NOT EXISTS file_size_bytes BIGINT;

ALTER TABLE employee_document
    ADD COLUMN IF NOT EXISTS uploaded_at TIMESTAMP;

ALTER TABLE employee_document
    ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP;

ALTER TABLE employee_document
    ADD COLUMN IF NOT EXISTS reviewed_by_employee_id BIGINT REFERENCES employee(id);

ALTER TABLE employee_document
    ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(500);