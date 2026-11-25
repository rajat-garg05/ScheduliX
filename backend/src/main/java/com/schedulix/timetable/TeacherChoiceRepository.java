package com.schedulix.timetable;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeacherChoiceRepository extends JpaRepository<TeacherChoiceEntity, TeacherChoiceId> {

    List<TeacherChoiceEntity> findByTimetableId(UUID timetableId);

    void deleteByTimetableId(UUID timetableId);

    boolean existsByTeacherId(UUID teacherId);

    boolean existsBySubjectId(UUID subjectId);
}