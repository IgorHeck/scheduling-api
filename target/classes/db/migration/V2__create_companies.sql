CREATE TABLE companies (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(150) NOT NULL,
    description          TEXT,
    address              VARCHAR(255),
    phone                VARCHAR(20),
    allow_client_booking BOOLEAN NOT NULL DEFAULT TRUE,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    owner_id             BIGINT REFERENCES users(id),
    created_at           TIMESTAMP DEFAULT NOW()
);
