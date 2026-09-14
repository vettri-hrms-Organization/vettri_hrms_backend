ALTER TABLE software_version
    ADD COLUMN package_storage_key VARCHAR(500);

CREATE INDEX ix_software_version_storage_key
    ON software_version(package_storage_key);