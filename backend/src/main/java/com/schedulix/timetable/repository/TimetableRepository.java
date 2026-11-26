package com.schedulix.timetable.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.schedulix.timetable.entity.Timetable;
import com.schedulix.timetable.entity.TimetableStatus;

public interface TimetableRepository extends JpaRepository<Timetable, UUID> {

    List<Timetable> findByDepartmentIdAndStatusOrderByCreatedAtDesc(UUID departmentId, TimetableStatus status);

    List<Timetable> findByDepartmentIdOrderByCreatedAtDesc(UUID departmentId);

    Optional<Timetable> findByIdAndDepartmentId(UUID id, UUID departmentId);
}