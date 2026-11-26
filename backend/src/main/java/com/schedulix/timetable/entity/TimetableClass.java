package com.schedulix.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "timetable_class")
@IdClass(TimetableClassId.class)
public class TimetableClass {

    @Id
    @Column(name = "timetable_id", nullable = false)
    private UUID timetableId;

    @Id
    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "is_saved", nullable = false)
    private boolean saved;

    protected TimetableClass() {
    }

    public TimetableClass(UUID timetableId, UUID sectionId, boolean saved) {
        this.timetableId = timetableId;
        this.sectionId = sectionId;
        this.saved = saved;
    }

    public UUID getTimetableId() {
        return timetableId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }
}