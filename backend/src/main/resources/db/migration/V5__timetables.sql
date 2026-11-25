CREATE TABLE timetable (
    id UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES department(id),
    semester_id UUID NOT NULL REFERENCES semester(id),
    name VARCHAR(150) NOT NULL,
    status VARCHAR(10) NOT NULL CHECK (status IN ('DRAFT', 'SAVED')),
    settings JSONB NOT NULL,
    variation INTEGER NOT NULL DEFAULT 0,
    created_by UUID REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL,
    saved_at TIMESTAMPTZ
);

CREATE INDEX idx_timetable_department_status ON timetable (department_id, status);

CREATE TABLE timetable_class (
    timetable_id UUID NOT NULL REFERENCES timetable(id) ON DELETE CASCADE,
    section_id UUID NOT NULL REFERENCES section(id) ON DELETE CASCADE,
    is_saved BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (timetable_id, section_id)
);

CREATE UNIQUE INDEX uq_timetable_class_saved_section
    ON timetable_class (section_id)
    WHERE is_saved;

CREATE TABLE teacher_choice (
    timetable_id UUID NOT NULL REFERENCES timetable(id) ON DELETE CASCADE,
    section_id UUID NOT NULL REFERENCES section(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject(id),
    teacher_id UUID NOT NULL REFERENCES teacher(id),
    PRIMARY KEY (timetable_id, section_id, subject_id)
);

CREATE INDEX idx_teacher_choice_teacher ON teacher_choice (teacher_id);
CREATE INDEX idx_teacher_choice_subject ON teacher_choice (subject_id);

CREATE TABLE fixed_lecture (
    id UUID PRIMARY KEY,
    timetable_id UUID NOT NULL REFERENCES timetable(id) ON DELETE CASCADE,
    section_id UUID NOT NULL REFERENCES section(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject(id),
    day_of_week VARCHAR(10) NOT NULL,
    start_lecture SMALLINT NOT NULL CHECK (start_lecture >= 1),
    UNIQUE (timetable_id, section_id, subject_id, day_of_week, start_lecture)
);

CREATE TABLE lecture (
    id UUID PRIMARY KEY,
    timetable_id UUID NOT NULL REFERENCES timetable(id) ON DELETE CASCADE,
    section_id UUID NOT NULL REFERENCES section(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject(id),
    teacher_id UUID NOT NULL REFERENCES teacher(id),
    day_of_week VARCHAR(10) NOT NULL,
    start_lecture SMALLINT NOT NULL,
    length SMALLINT NOT NULL CHECK (length BETWEEN 1 AND 3),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    kept BOOLEAN NOT NULL DEFAULT FALSE,
    group_key UUID
);

CREATE INDEX idx_lecture_timetable_section ON lecture (timetable_id, section_id);
CREATE INDEX idx_lecture_teacher_day ON lecture (teacher_id, day_of_week);