CREATE TABLE software_package (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    publisher VARCHAR(150),
    description VARCHAR(2000),
    platform VARCHAR(20) NOT NULL DEFAULT 'WINDOWS',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_software_package_company FOREIGN KEY (company_id) REFERENCES company(id)
);

CREATE UNIQUE INDEX ux_software_package_company_name
    ON software_package(company_id, name)
    WHERE deleted = FALSE;

CREATE TABLE software_version (
    id BIGSERIAL PRIMARY KEY,
    software_package_id BIGINT NOT NULL,
    package_version VARCHAR(50) NOT NULL,
    architecture VARCHAR(20) NOT NULL DEFAULT 'x64',
    installer_type VARCHAR(20) NOT NULL DEFAULT 'EXE',
    installer_url VARCHAR(2048),
    checksum_sha256 VARCHAR(128),
    file_size_bytes BIGINT,
    silent_install_arguments VARCHAR(1000),
    detection_rule VARCHAR(1000),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_software_version_package FOREIGN KEY (software_package_id) REFERENCES software_package(id)
);

CREATE UNIQUE INDEX ux_software_version_package_version
    ON software_version(software_package_id, package_version)
    WHERE deleted = FALSE;

CREATE TABLE software_deployment (
    id BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL,
    software_version_id BIGINT NOT NULL,
    created_by_user_id BIGINT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    note VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_software_deployment_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT fk_software_deployment_version FOREIGN KEY (software_version_id) REFERENCES software_version(id)
);

CREATE TABLE software_deployment_target (
    id BIGSERIAL PRIMARY KEY,
    deployment_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    employee_id BIGINT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    error_message VARCHAR(2000),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    installed_version VARCHAR(50),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    CONSTRAINT fk_software_target_deployment FOREIGN KEY (deployment_id) REFERENCES software_deployment(id),
    CONSTRAINT fk_software_target_device FOREIGN KEY (device_id) REFERENCES monitored_device(id),
    CONSTRAINT fk_software_target_employee FOREIGN KEY (employee_id) REFERENCES employee(id),
    CONSTRAINT ux_software_target UNIQUE (deployment_id, device_id)
);

CREATE INDEX ix_software_deployment_company_status
    ON software_deployment(company_id, status);

CREATE INDEX ix_software_target_device
    ON software_deployment_target(device_id, status);
