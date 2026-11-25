package com.schedulix.setup;

import java.util.List;
import java.util.UUID;

public record PlanImportPreviewResponse(
        int totalRows,
        int newCount,
        int changedCount,
        int unchangedCount,
        int problemCount,
        int subjectCount,
        int totalLecturesPerWeek,
        boolean canConfirm,
        String summary,
        List<String> lockErrors,
        List<String> warnings,
        List<TargetSection> targetSections,
        List<PreviewRow> rows
) {
    public enum RowStatus {
        NEW,
        CHANGED,
        UNCHANGED,
        PROBLEM
    }

    public record TargetSection(UUID id, String name, boolean locked) {
    }

    public record PreviewRow(
            int rowNumber,
            RowStatus status,
            String subjectCode,
            String subjectName,
            String lecturesPerWeek,
            String message
    ) {
    }
}