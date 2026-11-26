package com.schedulix.setup.dto;

import java.time.Instant;
import java.util.UUID;
import com.schedulix.setup.entity.Teacher;

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