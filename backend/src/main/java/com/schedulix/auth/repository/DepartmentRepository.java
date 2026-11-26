package com.schedulix.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import com.schedulix.auth.entity.Department;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
}