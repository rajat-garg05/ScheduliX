package com.schedulix.timetable.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class TimetableClassId implements Serializable {

    private UUID timetableId;
    private UUID sectionId;

    public TimetableClassId() {
    }

    public TimetableClassId(UUID timetableId, UUID sectionId) {
        this.timetableId = timetableId;
        this.sectionId = sectionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimetableClassId that)) return false;
        return Objects.equals(timetableId, that.timetableId) && Objects.equals(sectionId, that.sectionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timetableId, sectionId);
    }
}