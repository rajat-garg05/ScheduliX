package com.schedulix.leave;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "substitute")
public class Substitute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "leave_id", nullable = false)
    private UUID leaveId;

    @Column(name = "lecture_id", nullable = false)
    private UUID lectureId;

    @Column(name = "on_date", nullable = false)
    private LocalDate onDate;

    @Column(name = "substitute_teacher_id")
    private UUID substituteTeacherId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubstituteStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Substitute() {
    }

    public Substitute(
            UUID leaveId,
            UUID lectureId,
            LocalDate onDate,
            UUID substituteTeacherId,
            SubstituteStatus status,
            Instant updatedAt
    ) {
        this.leaveId = leaveId;
        this.lectureId = lectureId;
        this.onDate = onDate;
        this.substituteTeacherId = substituteTeacherId;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
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

    public UUID getSubstituteTeacherId() {
        return substituteTeacherId;
    }

    public void setSubstituteTeacherId(UUID substituteTeacherId) {
        this.substituteTeacherId = substituteTeacherId;
    }

    public SubstituteStatus getStatus() {
        return status;
    }

    public void setStatus(SubstituteStatus status) {
        this.status = status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}