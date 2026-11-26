package com.schedulix.timetable.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.schedulix.timetable.entity.Lecture;

public interface LectureRepository extends JpaRepository<Lecture, UUID> {

    List<Lecture> findByTimetableId(UUID timetableId);

    Optional<Lecture> findByIdAndTimetableId(UUID id, UUID timetableId);

    List<Lecture> findByTimetableIdAndGroupKey(UUID timetableId, UUID groupKey);

    void deleteByTimetableId(UUID timetableId);

    boolean existsByTeacherId(UUID teacherId);

    boolean existsBySubjectId(UUID subjectId);

    @Query("""
            SELECT COUNT(l) > 0 FROM Lecture l
            JOIN Timetable t ON t.id = l.timetableId
            WHERE l.subjectId = :subjectId AND t.status = com.schedulix.timetable.entity.TimetableStatus.SAVED
            """)
    boolean existsSavedBySubjectId(@Param("subjectId") UUID subjectId);

    @Query("""
            SELECT l FROM Lecture l
            JOIN Timetable t ON t.id = l.timetableId
            WHERE t.departmentId = :departmentId
              AND t.status = com.schedulix.timetable.entity.TimetableStatus.SAVED
            """)
    List<Lecture> findSavedLecturesByDepartment(@Param("departmentId") UUID departmentId);
}