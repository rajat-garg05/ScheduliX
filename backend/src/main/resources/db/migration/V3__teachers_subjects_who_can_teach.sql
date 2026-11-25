CREATE TABLE teacher (
    id UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES department(id),
    code VARCHAR(30) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    designation VARCHAR(100) NOT NULL DEFAULT '',
    user_account_id UUID NOT NULL UNIQUE REFERENCES user_account(id),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (department_id, code)
);

CREATE TABLE subject (
    id UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES department(id),
    code VARCHAR(30) NOT NULL,
    name VARCHAR(150) NOT NULL,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('THEORY', 'LAB')),
    periods_in_a_row SMALLINT NOT NULL DEFAULT 1 CHECK (periods_in_a_row BETWEEN 1 AND 3),
    run_together_group VARCHAR(60),
    shared_place VARCHAR(80),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (department_id, code),
    CHECK (run_together_group IS NULL OR kind = 'LAB')
);

CREATE TABLE teacher_subject (
    teacher_id UUID NOT NULL REFERENCES teacher(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject(id) ON DELETE CASCADE,
    PRIMARY KEY (teacher_id, subject_id)
);

CREATE INDEX idx_teacher_subject_subject ON teacher_subject (subject_id);