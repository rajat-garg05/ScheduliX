package com.schedulix.leave.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leave_lecture")
@IdClass(LeaveLectureId.class)
public class LeaveLecture {

    @Id
    @Column(name = "leave_id", nullable = false)
    private UUID leaveId;

    @Id
    @Column(name = "lecture_id", nullable = false)
    private UUID lectureId;

    @Id
    @Column(name = "on_date", nullable = false)
    private LocalDate onDate;

    protected LeaveLecture() {
    }

    public LeaveLecture(UUID leaveId, UUID lectureId, LocalDate onDate) {
        this.leaveId = leaveId;
        this.lectureId = lectureId;
        this.onDate = onDate;
    }

    public UUID getLeaveId() {
        return leaveId;
    }

    public UUID getLectureId() {
        return lectureId;
    }

    public LocalDate getOnDate() {
        return onDate;
    }
}