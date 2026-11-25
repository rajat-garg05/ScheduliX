package com.schedulix.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "history_entry")
public class HistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "user_account_id")
    private UUID userAccountId;

    @Column(name = "user_name", nullable = false, length = 150)
    private String userName;

    @Column(nullable = false, length = 30)
    private String kind;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected HistoryEntry() {
    }

    public HistoryEntry(UUID departmentId, UUID userAccountId, String userName, String kind, String text) {
        this.departmentId = departmentId;
        this.userAccountId = userAccountId;
        this.userName = userName;
        this.kind = kind;
        this.text = text;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public UUID getUserAccountId() {
        return userAccountId;
    }

    public String getUserName() {
        return userName;
    }

    public String getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}