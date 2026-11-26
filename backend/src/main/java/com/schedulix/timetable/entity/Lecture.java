package com.schedulix.timetable.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "lecture")
public class Lecture {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "timetable_id", nullable = false)
    private UUID timetableId;

    @Column(name = "section_id", nullable = false)
    private UUID sectionId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_lecture", nullable = false)
    private short startLecture;

    @Column(nullable = false)
    private short length;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private boolean kept;

    @Column(name = "group_key")
    private UUID groupKey;

    protected Lecture() {
    }

    public Lecture(
            UUID timetableId,
            UUID sectionId,
            UUID subjectId,
            UUID teacherId,
            DayOfWeek dayOfWeek,
            short startLecture,
            short length,
            LocalTime startTime,
            LocalTime endTime,
            boolean kept,
            UUID groupKey
    ) {
        this.timetableId = timetableId;
        this.sectionId = sectionId;
        this.subjectId = subjectId;
        this.teacherId = teacherId;
        this.dayOfWeek = dayOfWeek;
        this.startLecture = startLecture;
        this.length = length;
        this.startTime = startTime;
        this.endTime = endTime;
        this.kept = kept;
        this.groupKey = groupKey;
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

    public UUID getTeacherId() {
        return teacherId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public short getStartLecture() {
        return startLecture;
    }

    public short getLength() {
        return length;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public boolean isKept() {
        return kept;
    }

    public void setKept(boolean kept) {
        this.kept = kept;
    }

    public UUID getGroupKey() {
        return groupKey;
    }
}