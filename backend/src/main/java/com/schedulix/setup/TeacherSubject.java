package com.schedulix.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "teacher_subject")
@IdClass(TeacherSubjectId.class)
public class TeacherSubject {

    @Id
    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Id
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    protected TeacherSubject() {
    }

    public TeacherSubject(UUID teacherId, UUID subjectId) {
        this.teacherId = teacherId;
        this.subjectId = subjectId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }
}