CREATE TABLE department (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES department(id),
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('HOD', 'TEACHER')),
    status VARCHAR(10) NOT NULL CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED')),
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_user_account_hod_per_department
    ON user_account (department_id)
    WHERE role = 'HOD';

CREATE TABLE email_token (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    purpose VARCHAR(10) NOT NULL CHECK (purpose IN ('VERIFY', 'RESET')),
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_email_token_user_purpose
    ON email_token (user_account_id, purpose);

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notification (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    text VARCHAR(500) NOT NULL,
    link VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_notification_user_read_created
    ON notification (user_account_id, read_at, created_at DESC);

CREATE TABLE history_entry (
    id UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES department(id),
    user_account_id UUID REFERENCES user_account(id) ON DELETE SET NULL,
    user_name VARCHAR(150) NOT NULL,
    kind VARCHAR(30) NOT NULL,
    text VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_history_entry_dept_created
    ON history_entry (department_id, created_at DESC);