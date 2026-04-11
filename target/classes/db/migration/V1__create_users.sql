CREATE TABLE users (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120)  NOT NULL,
    email      VARCHAR(180)  NOT NULL UNIQUE,
    password   VARCHAR(255)  NOT NULL,
    phone      VARCHAR(20),
    role       VARCHAR(20)   NOT NULL DEFAULT 'CLIENT',
    active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP     DEFAULT NOW(),
    updated_at TIMESTAMP     DEFAULT NOW()
);
