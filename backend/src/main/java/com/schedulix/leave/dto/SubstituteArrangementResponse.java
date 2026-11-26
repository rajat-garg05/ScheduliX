package com.schedulix.leave.dto;

import java.time.LocalDate;
import java.util.UUID;
import com.schedulix.leave.entity.SubstituteStatus;

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