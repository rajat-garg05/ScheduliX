package com.schedulix.leave.entity;

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
@Table(name = "leave")
public class Leave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private LeaveScope scope;

    @Column(nullable = false, length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LeaveStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Leave() {
    }

    public Leave(
            UUID departmentId,
            UUID teacherId,
            LocalDate fromDate,
            LocalDate toDate,
            LeaveScope scope,
            String reason,
            LeaveStatus status,
            UUID decidedBy,
            String decisionNote,
            UUID createdBy,
            Instant createdAt
    ) {
        this.departmentId = departmentId;
        this.teacherId = teacherId;
        this.fromDate = fromDate;
        this.toDate = toDate;
        this.scope = scope;
        this.reason = reason == null ? "" : reason;
        this.status = status;
        this.decidedBy = decidedBy;
        this.decisionNote = decisionNote;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public UUID getTeacherId() {
        return teacherId;
    }

    public LocalDate getFromDate() {
        return fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public LeaveScope getScope() {
        return scope;
    }

    public String getReason() {
        return reason;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public void setStatus(LeaveStatus status) {
        this.status = status;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(UUID decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public void setDecisionNote(String decisionNote) {
        this.decisionNote = decisionNote;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}