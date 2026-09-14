CREATE TABLE software_deployment_stage_event (
    id BIGSERIAL PRIMARY KEY,
    target_id BIGINT NOT NULL REFERENCES software_deployment_target(id),
    status VARCHAR(40) NOT NULL,
    error_code VARCHAR(100),
    error_message VARCHAR(2000),
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ix_software_stage_event_target_time
    ON software_deployment_stage_event(target_id, occurred_at, id);