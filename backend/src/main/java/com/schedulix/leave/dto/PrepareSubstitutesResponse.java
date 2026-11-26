package com.schedulix.leave.dto;

import java.time.LocalDate;

public record PrepareSubstitutesResponse(
        LocalDate date,
        int preparedCount,
        int totalForDate,
        String message
) {
}