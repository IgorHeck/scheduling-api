CREATE TABLE appointments (
    id              BIGSERIAL PRIMARY KEY,
    company_id      BIGINT NOT NULL REFERENCES companies(id),
    client_id       BIGINT NOT NULL REFERENCES users(id),
    professional_id BIGINT NOT NULL REFERENCES users(id),
    start_at        TIMESTAMP   NOT NULL,
    end_at          TIMESTAMP   NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    notes           TEXT,
    cancel_reason   VARCHAR(255),
    created_at      TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_appointments_company ON appointments(company_id);
CREATE INDEX idx_appointments_client  ON appointments(client_id);
CREATE INDEX idx_appointments_start   ON appointments(start_at);
