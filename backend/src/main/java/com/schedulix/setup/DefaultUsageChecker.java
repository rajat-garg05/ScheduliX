package com.schedulix.setup;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DefaultUsageChecker implements UsageChecker {

    @Override
    public boolean isTeacherUsed(UUID teacherId) {
        return false;
    }

    @Override
    public boolean isSubjectUsed(UUID subjectId) {
        return false;
    }

    @Override
    public boolean isSubjectUsedInSavedTimetable(UUID subjectId) {
        return false;
    }
}