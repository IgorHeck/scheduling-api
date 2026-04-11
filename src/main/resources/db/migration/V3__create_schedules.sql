CREATE TABLE schedules (
    id                    BIGSERIAL PRIMARY KEY,
    company_id            BIGINT NOT NULL REFERENCES companies(id),
    professional_id       BIGINT NOT NULL REFERENCES users(id),
    day_of_week           VARCHAR(15) NOT NULL,
    start_time            TIME NOT NULL,
    end_time              TIME NOT NULL,
    slot_duration_minutes INT  NOT NULL DEFAULT 60,
    active                BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE schedule_blocks (
    id         BIGSERIAL PRIMARY KEY,
    company_id BIGINT NOT NULL REFERENCES companies(id),
    start_at   TIMESTAMP NOT NULL,
    end_at     TIMESTAMP NOT NULL,
    reason     VARCHAR(255)
);
