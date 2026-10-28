-- Company working week: comma list of weekly offs (e.g. 'SATURDAY,SUNDAY', 'SUNDAY',
-- or 'SUNDAY,SATURDAY#2,SATURDAY#4' for 2nd/4th Saturday off). Existing companies keep Sat/Sun.
ALTER TABLE company
    ADD COLUMN weekly_off_days VARCHAR(100) NOT NULL DEFAULT 'SATURDAY,SUNDAY';

-- Half-day leave: a single-date request covering either the first or the second half (0.5 day).
ALTER TABLE leave_request
    ADD COLUMN half_day BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN half_day_session VARCHAR(20);

ALTER TABLE leave_request
    ADD CONSTRAINT chk_leave_request_half_day_session
        CHECK ((half_day = FALSE AND half_day_session IS NULL)
            OR (half_day = TRUE AND half_day_session IN ('FIRST_HALF', 'SECOND_HALF')));
