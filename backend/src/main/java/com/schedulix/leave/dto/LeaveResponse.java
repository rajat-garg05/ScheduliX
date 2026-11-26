package com.schedulix.leave.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.schedulix.leave.entity.LeaveScope;
import com.schedulix.leave.entity.LeaveStatus;

public record LeaveResponse(
        UUID id,
        UUID teacherId,
        String teacherCode,
        String teacherName,
        LocalDate fromDate,
        LocalDate toDate,
        LeaveScope scope,
        String reason,
        LeaveStatus status,
        String decisionNote,
        List<LeaveLectureItem> lectures,
        Instant createdAt
) {
    public record LeaveLectureItem(UUID lectureId, LocalDate onDate) {
    }
}