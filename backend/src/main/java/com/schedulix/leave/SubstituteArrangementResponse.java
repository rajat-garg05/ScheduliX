package com.schedulix.leave;

import java.time.LocalDate;
import java.util.UUID;

public record SubstituteArrangementResponse(
        UUID id,
        UUID leaveId,
        UUID lectureId,
        LocalDate onDate,
        UUID substituteTeacherId,
        String substituteTeacherName,
        SubstituteStatus status,
        String warning
) {
}