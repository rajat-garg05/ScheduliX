package com.schedulix.timetable.dto;

import java.util.List;

public record TimetableSettingsResponse(
        TimetableSettings settings,
        List<DayPreviewSlot> dayPreview
) {
}