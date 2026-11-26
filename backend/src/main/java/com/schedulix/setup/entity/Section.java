package com.schedulix.setup.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "section")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "semester_id", nullable = false)
    private UUID semesterId;

    @Column(nullable = false, length = 30)
    private String name;

    protected Section() {
    }

    public Section(UUID semesterId, String name) {
        this.semesterId = semesterId;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSemesterId() {
        return semesterId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}