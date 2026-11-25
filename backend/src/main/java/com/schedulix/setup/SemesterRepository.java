package com.schedulix.setup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemesterRepository extends JpaRepository<Semester, UUID> {

    List<Semester> findByDepartmentIdOrderByNumberAsc(UUID departmentId);

    Optional<Semester> findByIdAndDepartmentId(UUID id, UUID departmentId);

    Optional<Semester> findByDepartmentIdAndNumber(UUID departmentId, short number);

    boolean existsByDepartmentIdAndNumber(UUID departmentId, short number);
}