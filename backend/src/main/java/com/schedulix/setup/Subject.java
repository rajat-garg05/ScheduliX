package com.schedulix.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subject")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SubjectKind kind;

    @Column(name = "periods_in_a_row", nullable = false)
    private short periodsInARow;

    @Column(name = "run_together_group", length = 60)
    private String runTogetherGroup;

    @Column(name = "shared_place", length = 80)
    private String sharedPlace;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Subject() {
    }

    public Subject(
            UUID departmentId,
            String code,
            String name,
            SubjectKind kind,
            short periodsInARow,
            String runTogetherGroup,
            String sharedPlace
    ) {
        this.departmentId = departmentId;
        this.code = code;
        this.name = name;
        this.kind = kind;
        this.periodsInARow = periodsInARow;
        this.runTogetherGroup = runTogetherGroup;
        this.sharedPlace = sharedPlace;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SubjectKind getKind() {
        return kind;
    }

    public void setKind(SubjectKind kind) {
        this.kind = kind;
    }

    public short getPeriodsInARow() {
        return periodsInARow;
    }

    public void setPeriodsInARow(short periodsInARow) {
        this.periodsInARow = periodsInARow;
    }

    public String getRunTogetherGroup() {
        return runTogetherGroup;
    }

    public void setRunTogetherGroup(String runTogetherGroup) {
        this.runTogetherGroup = runTogetherGroup;
    }

    public String getSharedPlace() {
        return sharedPlace;
    }

    public void setSharedPlace(String sharedPlace) {
        this.sharedPlace = sharedPlace;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}