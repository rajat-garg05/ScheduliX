package com.schedulix.timetable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TeacherChoiceId implements Serializable {

    private UUID timetableId;
    private UUID sectionId;
    private UUID subjectId;

    public TeacherChoiceId() {
    }

    public TeacherChoiceId(UUID timetableId, UUID sectionId, UUID subjectId) {
        this.timetableId = timetableId;
        this.sectionId = sectionId;
        this.subjectId = subjectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeacherChoiceId that)) return false;
        return Objects.equals(timetableId, that.timetableId)
                && Objects.equals(sectionId, that.sectionId)
                && Objects.equals(subjectId, that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timetableId, sectionId, subjectId);
    }
}