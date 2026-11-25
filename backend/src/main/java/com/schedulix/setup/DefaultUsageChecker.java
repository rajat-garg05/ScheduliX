package com.schedulix.setup;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DefaultUsageChecker implements UsageChecker {

    private final SectionSubjectRepository sectionSubjectRepository;

    public DefaultUsageChecker(SectionSubjectRepository sectionSubjectRepository) {
        this.sectionSubjectRepository = sectionSubjectRepository;
    }

    @Override
    public boolean isTeacherUsed(UUID teacherId) {
        return false;
    }

    @Override
    public boolean isSubjectUsed(UUID subjectId) {
        return sectionSubjectRepository.existsBySubjectId(subjectId);
    }

    @Override
    public boolean isSubjectUsedInSavedTimetable(UUID subjectId) {
        return false;
    }
}