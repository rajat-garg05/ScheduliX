package com.schedulix.setup;

import java.util.List;
import java.util.UUID;

public record SubjectTeachersRequest(List<UUID> teacherIds) {
}