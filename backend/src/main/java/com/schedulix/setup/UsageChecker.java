package com.schedulix.setup;

import java.util.UUID;

public interface UsageChecker {

    boolean isTeacherUsed(UUID teacherId);

    boolean isSubjectUsed(UUID subjectId);

    boolean isSubjectUsedInSavedTimetable(UUID subjectId);
}