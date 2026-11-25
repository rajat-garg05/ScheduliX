package com.schedulix.leave;

import java.util.UUID;

public record SubstituteOptionResponse(
        UUID teacherId,
        String teacherCode,
        String teacherName,
        String status,
        String label,
        boolean selectable,
        boolean warning
) {
}