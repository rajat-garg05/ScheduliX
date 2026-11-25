package com.schedulix.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimetableClassRepository extends JpaRepository<TimetableClass, TimetableClassId> {

    List<TimetableClass> findByTimetableId(UUID timetableId);

    void deleteByTimetableId(UUID timetableId);

    boolean existsBySectionIdAndSavedTrue(UUID sectionId);
}