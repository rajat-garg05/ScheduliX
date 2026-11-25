package com.schedulix.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LectureRepository extends JpaRepository<Lecture, UUID> {

    List<Lecture> findByTimetableId(UUID timetableId);

    void deleteByTimetableId(UUID timetableId);

    boolean existsByTeacherId(UUID teacherId);

    boolean existsBySubjectId(UUID subjectId);

    @Query("""
            SELECT COUNT(l) > 0 FROM Lecture l
            JOIN Timetable t ON t.id = l.timetableId
            WHERE l.subjectId = :subjectId AND t.status = com.schedulix.timetable.TimetableStatus.SAVED
            """)
    boolean existsSavedBySubjectId(@Param("subjectId") UUID subjectId);

    @Query("""
            SELECT l FROM Lecture l
            JOIN Timetable t ON t.id = l.timetableId
            WHERE t.departmentId = :departmentId
              AND t.status = com.schedulix.timetable.TimetableStatus.SAVED
            """)
    List<Lecture> findSavedLecturesByDepartment(@Param("departmentId") UUID departmentId);
}