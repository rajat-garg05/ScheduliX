package com.schedulix.timetable;

import java.util.List;

public record TimetableMakeResponse(
        boolean ok,
        List<String> messages,
        TimetableViewResponse view
) {
}