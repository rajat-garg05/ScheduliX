package com.schedulix.setup.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.schedulix.setup.entity.Subject;
import com.schedulix.setup.entity.SubjectKind;

public record SubjectResponse(
        UUID id,
        String code,
        String name,
        SubjectKind kind,
        int periodsInARow,
        String runTogetherGroup,
        String sharedPlace,
        boolean active,
        Instant createdAt,
        List<String> warnings
) {
    public static SubjectResponse from(Subject subject) {
        return from(subject, List.of());
    }

    public static SubjectResponse from(Subject subject, List<String> warnings) {
        return new SubjectResponse(
                subject.getId(),
                subject.getCode(),
                subject.getName(),
                subject.getKind(),
                subject.getPeriodsInARow(),
                subject.getRunTogetherGroup(),
                subject.getSharedPlace(),
                subject.isActive(),
                subject.getCreatedAt(),
                warnings
        );
    }
}