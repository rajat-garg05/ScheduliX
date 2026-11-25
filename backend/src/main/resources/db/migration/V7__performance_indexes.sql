-- Speeds up loading a class timetable grid by timetable and section
CREATE INDEX IF NOT EXISTS idx_lecture_timetable_section
    ON lecture (timetable_id, section_id);

-- Speeds up teacher conflict checks, Today, and My Timetable
CREATE INDEX IF NOT EXISTS idx_lecture_teacher_day
    ON lecture (teacher_id, day_of_week);

-- Speeds up checking whether a section has a saved timetable
CREATE INDEX IF NOT EXISTS idx_timetable_class_section_saved
    ON timetable_class (section_id, is_saved);

-- Speeds up the notification bell and paginated notification list
CREATE INDEX IF NOT EXISTS idx_notification_user_created
    ON notification (user_account_id, created_at DESC);

-- Speeds up the department History feed
CREATE INDEX IF NOT EXISTS idx_history_department_created
    ON history_entry (department_id, created_at DESC);