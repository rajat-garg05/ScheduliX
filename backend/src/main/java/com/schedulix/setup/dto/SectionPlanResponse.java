package com.schedulix.setup.dto;

import java.util.List;
import java.util.UUID;
import com.schedulix.setup.entity.SubjectKind;

public record SectionPlanResponse(
        UUID sectionId,
        String sectionName,
        UUID semesterId,
        String semesterName,
        boolean locked,
        String lockMessage,
        int subjectCount,
        int totalLecturesPerWeek,
        List<String> warnings,
        List<PlanRow> items
) {
    public record PlanRow(
            UUID subjectId,
            String subjectCode,
            String subjectName,
            SubjectKind kind,
            int periodsInARow,
            String runTogetherGroup,
            String sharedPlace,
            int lecturesPerWeek,
            boolean hasTeacher
    ) {
    }
}