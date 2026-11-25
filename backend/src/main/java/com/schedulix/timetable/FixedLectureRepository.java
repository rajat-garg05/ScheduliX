package com.schedulix.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FixedLectureRepository extends JpaRepository<FixedLectureEntity, UUID> {

    List<FixedLectureEntity> findByTimetableId(UUID timetableId);

    Optional<FixedLectureEntity> findByIdAndTimetableId(UUID id, UUID timetableId);

    void deleteByTimetableId(UUID timetableId);

    boolean existsBySubjectId(UUID subjectId);
}