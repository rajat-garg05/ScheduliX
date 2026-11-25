package com.schedulix.setup;

import java.time.Instant;
import java.util.UUID;

public record TeacherResponse(
        UUID id,
        String code,
        String fullName,
        String email,
        String designation,
        boolean active,
        Instant createdAt
) {
    public static TeacherResponse from(Teacher teacher) {
        return new TeacherResponse(
                teacher.getId(),
                teacher.getCode(),
                teacher.getFullName(),
                teacher.getEmail(),
                teacher.getDesignation(),
                teacher.isActive(),
                teacher.getCreatedAt()
        );
    }
}