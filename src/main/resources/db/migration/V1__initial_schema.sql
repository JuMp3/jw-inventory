-- =============================================================
-- jw-inventory initial schema
-- Timestamps are LocalDateTime in the application timezone (app.timezone).
-- =============================================================

CREATE TABLE congregations
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX ux_congregations_name ON congregations (LOWER(name));

CREATE TABLE users
(
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    full_name       VARCHAR(150) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('SUPER_ADMIN', 'ADMIN', 'USER')),
    congregation_id BIGINT REFERENCES congregations (id),
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    -- the SuperAdmin belongs to no congregation; everyone else belongs to exactly one
    CONSTRAINT ck_users_congregation_by_role CHECK ((role = 'SUPER_ADMIN') = (congregation_id IS NULL))
);
-- at most one SuperAdmin can ever exist
CREATE UNIQUE INDEX ux_users_single_super_admin ON users (role) WHERE role = 'SUPER_ADMIN';

CREATE TABLE publications
(
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(50)  NOT NULL,
    title            VARCHAR(255) NOT NULL,
    quantity         INTEGER      NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    image_updated_at TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX ux_publications_code ON publications (LOWER(code));

-- image kept in its own table so listing publications never loads the bytes
CREATE TABLE publication_images
(
    publication_id BIGINT PRIMARY KEY REFERENCES publications (id) ON DELETE CASCADE,
    content_type   VARCHAR(50) NOT NULL,
    data           BYTEA       NOT NULL
);

CREATE TABLE stock_movements
(
    id                BIGSERIAL PRIMARY KEY,
    publication_id    BIGINT       NOT NULL REFERENCES publications (id) ON DELETE CASCADE,
    type              VARCHAR(20)  NOT NULL CHECK (type IN ('IN', 'OUT', 'SET', 'INVENTORY')),
    delta             INTEGER      NOT NULL,
    quantity_after    INTEGER      NOT NULL,
    user_name         VARCHAR(150) NOT NULL,
    congregation_name VARCHAR(120),
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_stock_movements_created ON stock_movements (created_at DESC);
CREATE INDEX ix_stock_movements_publication ON stock_movements (publication_id, created_at DESC);

CREATE TABLE inventory_sessions
(
    id         BIGSERIAL PRIMARY KEY,
    status     VARCHAR(20)  NOT NULL CHECK (status IN ('OPEN', 'CLOSED')),
    started_at TIMESTAMP    NOT NULL,
    started_by VARCHAR(150) NOT NULL,
    closed_at  TIMESTAMP,
    closed_by  VARCHAR(150)
);
-- only one inventory can be in progress (the library is shared)
CREATE UNIQUE INDEX ux_inventory_single_open ON inventory_sessions (status) WHERE status = 'OPEN';

CREATE TABLE inventory_items
(
    id                BIGSERIAL PRIMARY KEY,
    session_id        BIGINT       NOT NULL REFERENCES inventory_sessions (id) ON DELETE CASCADE,
    publication_id    BIGINT       NOT NULL REFERENCES publications (id) ON DELETE CASCADE,
    counted_quantity  INTEGER      NOT NULL CHECK (counted_quantity >= 0),
    -- system quantity at closing time; NULL while the session is open
    expected_quantity INTEGER,
    counted_by        VARCHAR(150) NOT NULL,
    counted_at        TIMESTAMP    NOT NULL,
    CONSTRAINT ux_inventory_items_session_publication UNIQUE (session_id, publication_id)
);

CREATE TABLE scheduled_notifications
(
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    subject       VARCHAR(255) NOT NULL,
    body          TEXT         NOT NULL,
    recipients    TEXT         NOT NULL,
    schedule_type VARCHAR(20)  NOT NULL CHECK (schedule_type IN ('ONCE', 'DAILY', 'WEEKLY', 'MONTHLY')),
    run_date      DATE,
    day_of_week   INTEGER CHECK (day_of_week BETWEEN 1 AND 7),
    day_of_month  INTEGER CHECK (day_of_month BETWEEN 1 AND 31),
    send_time     TIME         NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    -- SUPER_ADMIN = SuperAdmin only; ADMIN = shared with the admins (edit) and users (read-only)
    -- of its congregation; USER = personal, owner only. The SuperAdmin sees everything.
    scope           VARCHAR(20)  NOT NULL CHECK (scope IN ('SUPER_ADMIN', 'ADMIN', 'USER')),
    -- creator's congregation at creation time
    congregation_id BIGINT REFERENCES congregations (id) ON DELETE CASCADE,
    owner_id        BIGINT REFERENCES users (id) ON DELETE SET NULL,
    owner_name      VARCHAR(150) NOT NULL,
    next_run_at     TIMESTAMP,
    last_sent_at    TIMESTAMP,
    last_error      VARCHAR(1000),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_notifications_congregation CHECK ((scope = 'SUPER_ADMIN') = (congregation_id IS NULL))
);
CREATE INDEX ix_notifications_due ON scheduled_notifications (next_run_at) WHERE enabled;
CREATE INDEX ix_notifications_congregation ON scheduled_notifications (congregation_id);
