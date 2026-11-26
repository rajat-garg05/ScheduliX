package com.schedulix.leave.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.schedulix.leave.entity.LeaveScope;

public record CreateLeaveRequest(
        UUID teacherId,

        @NotNull(message = "Please pick a start date.")
        LocalDate fromDate,

        @NotNull(message = "Please pick an end date.")
        LocalDate toDate,

        @NotNull(message = "Please choose whole days or specific lectures.")
        LeaveScope scope,

        String reason,

        List<SelectedLecture> lectures
) {
    public record SelectedLecture(UUID lectureId, LocalDate onDate) {
    }
}