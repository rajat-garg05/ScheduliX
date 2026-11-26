package com.schedulix.timetable.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.schedulix.timetable.entity.TimetableClass;
import com.schedulix.timetable.entity.TimetableClassId;

public interface TimetableClassRepository extends JpaRepository<TimetableClass, TimetableClassId> {

    List<TimetableClass> findByTimetableId(UUID timetableId);

    void deleteByTimetableId(UUID timetableId);

    boolean existsBySectionIdAndSavedTrue(UUID sectionId);

    Optional<TimetableClass> findBySectionIdAndSavedTrue(UUID sectionId);
}