package com.schedulix.setup;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "teacher")
public class Teacher {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String designation;

    @Column(name = "user_account_id", nullable = false, unique = true)
    private UUID userAccountId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Teacher() {
    }

    public Teacher(
            UUID departmentId,
            String code,
            String fullName,
            String email,
            String designation,
            UUID userAccountId
    ) {
        this.departmentId = departmentId;
        this.code = code;
        this.fullName = fullName;
        this.email = email;
        this.designation = designation == null ? "" : designation;
        this.userAccountId = userAccountId;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation == null ? "" : designation;
    }

    public UUID getUserAccountId() {
        return userAccountId;
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