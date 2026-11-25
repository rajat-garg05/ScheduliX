package com.schedulix.timetable;

import java.util.List;

public record TimetableSettingsResponse(
        TimetableSettings settings,
        List<DayPreviewSlot> dayPreview
) {
}