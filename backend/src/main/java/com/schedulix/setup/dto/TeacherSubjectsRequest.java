package com.schedulix.setup.dto;

import java.util.List;
import java.util.UUID;

public record TeacherSubjectsRequest(List<UUID> subjectIds) {
}