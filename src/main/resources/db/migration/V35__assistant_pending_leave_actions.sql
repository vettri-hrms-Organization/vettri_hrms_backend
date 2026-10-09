CREATE TABLE assistant_pending_leave_action (
    conversation_id UUID PRIMARY KEY REFERENCES assistant_conversation(id) ON DELETE CASCADE,
    company_id BIGINT NOT NULL REFERENCES company(id),
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    employee_id BIGINT NOT NULL REFERENCES employee(id),
    leave_type_id BIGINT REFERENCES leave_type(id),
    leave_type_name VARCHAR(100),
    start_date DATE,
    end_date DATE,
    reason VARCHAR(500),
    requested_days DOUBLE PRECISION,
    remaining_days DOUBLE PRECISION,
    is_ready BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
