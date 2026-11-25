CREATE TABLE leave (
    id UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES department(id),
    teacher_id UUID NOT NULL REFERENCES teacher(id),
    from_date DATE NOT NULL,
    to_date DATE NOT NULL CHECK (to_date >= from_date),
    scope VARCHAR(15) NOT NULL CHECK (scope IN ('WHOLE_DAYS', 'SOME_LECTURES')),
    reason VARCHAR(300) NOT NULL DEFAULT '',
    status VARCHAR(10) NOT NULL CHECK (status IN ('ASKED', 'ACCEPTED', 'REJECTED')),
    decided_by UUID REFERENCES user_account(id),
    decision_note VARCHAR(300),
    created_by UUID NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_leave_department_status ON leave (department_id, status);
CREATE INDEX idx_leave_teacher_dates ON leave (teacher_id, from_date, to_date);

CREATE TABLE leave_lecture (
    leave_id UUID NOT NULL REFERENCES leave(id) ON DELETE CASCADE,
    lecture_id UUID NOT NULL REFERENCES lecture(id) ON DELETE CASCADE,
    on_date DATE NOT NULL,
    PRIMARY KEY (leave_id, lecture_id, on_date)
);

CREATE TABLE substitute (
    id UUID PRIMARY KEY,
    leave_id UUID NOT NULL REFERENCES leave(id) ON DELETE CASCADE,
    lecture_id UUID NOT NULL REFERENCES lecture(id) ON DELETE CASCADE,
    on_date DATE NOT NULL,
    substitute_teacher_id UUID REFERENCES teacher(id),
    status VARCHAR(10) NOT NULL CHECK (status IN ('NEEDED', 'PROPOSED', 'ACCEPTED')),
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (lecture_id, on_date)
);

CREATE INDEX idx_substitute_date ON substitute (on_date);
CREATE INDEX idx_substitute_teacher_date ON substitute (substitute_teacher_id, on_date);