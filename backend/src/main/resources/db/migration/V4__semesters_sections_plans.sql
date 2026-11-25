CREATE TABLE semester (
    id UUID PRIMARY KEY,
    department_id UUID NOT NULL REFERENCES department(id),
    number SMALLINT NOT NULL CHECK (number BETWEEN 1 AND 12),
    name VARCHAR(50) NOT NULL,
    UNIQUE (department_id, number)
);

CREATE TABLE section (
    id UUID PRIMARY KEY,
    semester_id UUID NOT NULL REFERENCES semester(id),
    name VARCHAR(30) NOT NULL,
    UNIQUE (semester_id, name)
);

CREATE TABLE section_subject (
    section_id UUID NOT NULL REFERENCES section(id) ON DELETE CASCADE,
    subject_id UUID NOT NULL REFERENCES subject(id),
    lectures_per_week SMALLINT NOT NULL CHECK (lectures_per_week > 0),
    PRIMARY KEY (section_id, subject_id)
);

CREATE INDEX idx_section_subject_subject ON section_subject (subject_id);