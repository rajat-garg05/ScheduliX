package com.schedulix.setup.service;

import java.util.UUID;

public interface UsageChecker {

    boolean isTeacherUsed(UUID teacherId);

    boolean isSubjectUsed(UUID subjectId);

    boolean isSubjectUsedInSavedTimetable(UUID subjectId);
}