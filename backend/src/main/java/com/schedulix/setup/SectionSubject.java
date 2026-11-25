package com.schedulix.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "section_subject")
@IdClass(SectionSubjectId.class)
public class SectionSubject {

    @Id
    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Id
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "lectures_per_week", nullable = false)
    private short lecturesPerWeek;

    protected SectionSubject() {
    }

    public SectionSubject(UUID sectionId, UUID subjectId, short lecturesPerWeek) {
        this.sectionId = sectionId;
        this.subjectId = subjectId;
        this.lecturesPerWeek = lecturesPerWeek;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public short getLecturesPerWeek() {
        return lecturesPerWeek;
    }

    public void setLecturesPerWeek(short lecturesPerWeek) {
        this.lecturesPerWeek = lecturesPerWeek;
    }
}