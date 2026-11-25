package com.schedulix.setup;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TeacherSubjectId implements Serializable {

    private UUID teacherId;
    private UUID subjectId;

    public TeacherSubjectId() {
    }

    public TeacherSubjectId(UUID teacherId, UUID subjectId) {
        this.teacherId = teacherId;
        this.subjectId = subjectId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeacherSubjectId that)) return false;
        return Objects.equals(teacherId, that.teacherId) && Objects.equals(subjectId, that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teacherId, subjectId);
    }
}