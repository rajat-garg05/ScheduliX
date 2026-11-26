package com.schedulix.setup.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class SectionSubjectId implements Serializable {

    private UUID sectionId;
    private UUID subjectId;

    public SectionSubjectId() {
    }

    public SectionSubjectId(UUID sectionId, UUID subjectId) {
        this.sectionId = sectionId;
        this.subjectId = subjectId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SectionSubjectId that)) return false;
        return Objects.equals(sectionId, that.sectionId) && Objects.equals(subjectId, that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sectionId, subjectId);
    }
}