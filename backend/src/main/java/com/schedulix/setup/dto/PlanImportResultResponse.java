package com.schedulix.setup.dto;

import java.util.List;

public record PlanImportResultResponse(
        List<String> appliedSections,
        int subjectCount,
        int totalLecturesPerWeek,
        List<String> warnings,
        String message
) {
}