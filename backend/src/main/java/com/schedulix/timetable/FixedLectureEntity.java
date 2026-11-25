package com.schedulix.timetable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.util.UUID;

@Entity
@Table(name = "fixed_lecture")
public class FixedLectureEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "timetable_id", nullable = false)
    private UUID timetableId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_lecture", nullable = false)
    private short startLecture;

    protected FixedLectureEntity() {
    }

    public FixedLectureEntity(
            UUID timetableId,
            UUID sectionId,
            UUID subjectId,
            DayOfWeek dayOfWeek,
            short startLecture
    ) {
        this.timetableId = timetableId;
        this.sectionId = sectionId;
        this.subjectId = subjectId;
        this.dayOfWeek = dayOfWeek;
        this.startLecture = startLecture;
    }

    public UUID getId() {
        return id;
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

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public short getStartLecture() {
        return startLecture;
    }
}