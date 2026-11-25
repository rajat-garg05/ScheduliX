package com.schedulix.timetable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "teacher_choice")
@IdClass(TeacherChoiceId.class)
public class TeacherChoiceEntity {

    @Id
    @Column(name = "timetable_id", nullable = false)
    private UUID timetableId;

    @Id
    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Id
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    protected TeacherChoiceEntity() {
    }

    public TeacherChoiceEntity(UUID timetableId, UUID sectionId, UUID subjectId, UUID teacherId) {
        this.timetableId = timetableId;
        this.sectionId = sectionId;
        this.subjectId = subjectId;
        this.teacherId = teacherId;
    }

    public UUID getTimetableId() {
        return timetableId;
    }

    public UUID getSectionId() {
        return sectionId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(UUID teacherId) {
        this.teacherId = teacherId;
    }
}